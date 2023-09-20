package jumper.filter;

import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.BasicAuthCredentials;
import jumper.model.config.JumperConfig;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.service.BasicAuthUtilService;
import jumper.service.HeaderUtil;
import jumper.service.OauthTokenUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

    private final CurrentTraceContext currentTraceContext;
    private final Tracer tracer;
    private final OauthTokenUtil oauthTokenUtil;
    private final BasicAuthUtilService basicAuthUtilService;

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    @Value( "${spring.application.name}")
    private String applicationName;

    public static final int REQUEST_FILTER_ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

    public RequestFilter(CurrentTraceContext currentTraceContext, Tracer tracer, OauthTokenUtil oauthTokenUtil, BasicAuthUtilService basicAuthUtilService) {
        super(Config.class);
        this.currentTraceContext = currentTraceContext;
        this.tracer = tracer;
        this.oauthTokenUtil = oauthTokenUtil;
        this.basicAuthUtilService = basicAuthUtilService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            WebFluxSleuthOperators.withSpanInScope(tracer, currentTraceContext, exchange, () -> {

                ServerHttpRequest request = exchange.getRequest();

                // checking to prevent later nullPointer on inconsistent state from Kong
                if (!request.getHeaders().containsKey(Constants.HEADER_REMOTE_API_URL)) {
                    throw new RuntimeException("missing mandatory header " + Constants.HEADER_REMOTE_API_URL);
                }

                // Prepare and extract JumperConfigValues
                JumperConfig jumperConfig = JumperConfig.parseConfigFrom(request);
                log.debug("JumperConfig encodedAsBase64: {}", JumperConfig.toBase64(jumperConfig));
                log.debug("JumperConfig decoded: {}", jumperConfig);

                // calculate routing stuff and add it to exchange and JumperConfig
                calculateRoutingStuff(request, exchange, config.getRoutePathPrefix(), jumperConfig);

                // store enhanced jumper_config for usage in Spectre
                exchange.getAttributes().put(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toBase64(jumperConfig));


                // handle request
                Optional<JumperInfoRequest> jumperInfoRequest = initializeJumperInfoRequest(jumperConfig);

                if (!jumperConfig.getRemoteApiUrl().startsWith(Constants.LOCALHOST_ISSUER_SERVICE)) {
                    if (Objects.isNull(jumperConfig.getInternalTokenEndpoint())) {
                        /** ALL NON MESH SCENARIOS **/

                        if (jumperConfig.getBasicAuth() != null && (jumperConfig.getBasicAuth().containsKey(jumperConfig.getConsumer()) || jumperConfig.getBasicAuth().containsKey(Constants.BASIC_AUTH_PROVIDER_KEY))) {
                            log.debug("----------------BASIC AUTH HEADER-------------");
                            jumperInfoRequest.ifPresent(i -> i.setInfoScenario(false, false, false, false, true));

                            BasicAuthCredentials basicAuthCredentials = jumperConfig.getBasicAuth().containsKey(jumperConfig.getConsumer()) ? jumperConfig.getBasicAuth().get(jumperConfig.getConsumer()) : jumperConfig.getBasicAuth().get(Constants.BASIC_AUTH_PROVIDER_KEY);
                            HeaderUtil.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BASIC + " " + basicAuthUtilService.encodeBasicAuth(basicAuthCredentials.getUsername(), basicAuthCredentials.getPassword()));
                        } else {


                            String lmsIssuer = localIssuerUrl + "/" + jumperConfig.getRealmName();

                            // Egress
                            if (Objects.nonNull(jumperConfig.getExternalTokenEndpoint())) {
                                log.debug("----------------EXTERNAL AUTHORIZATION-------------");
                                jumperInfoRequest.ifPresent(i -> i.setInfoScenario(false, false, false, true, false));

                                log.debug("Remote TokenEndpoint is set to: {}", jumperConfig.getExternalTokenEndpoint());

                                if (jumperConfig.getOauth() != null && jumperConfig.getOauth().containsKey(jumperConfig.getConsumer()) && jumperConfig.getOauth().get(jumperConfig.getConsumer()).getGrantType() != null && !jumperConfig.getOauth().get(jumperConfig.getConsumer()).getGrantType().isBlank()) {
                                    TokenInfo tokenInfo = oauthTokenUtil.getAccessToken(jumperConfig.getExternalTokenEndpoint(), jumperConfig.getOauth().get(jumperConfig.getConsumer()), jumperConfig.getConsumer());
                                    HeaderUtil.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER + " " + tokenInfo.getAccessToken());
                                } else {
                                    clientCredentialsFlow_legacy(exchange, jumperConfig);
                                }


                            } else if (Objects.nonNull(jumperConfig.getAccessTokenForwarding()) && Boolean.FALSE.equals(jumperConfig.getAccessTokenForwarding())) {
                                log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                                jumperInfoRequest.ifPresent(i -> i.setInfoScenario(true, true, false, false, false));

                                String lastmileSecurityToken = oauthTokenUtil.generateExtGatewayToken(jumperConfig.getEnvName(),
                                        jumperConfig.getConsumerToken(),
                                        String.valueOf(request.getMethod()),
                                        jumperConfig.getRequestPath(),
                                        lmsIssuer,
                                        getSecurityScopes(jumperConfig),
                                        request.getHeaders().getFirst(Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                                        request.getHeaders().getFirst(Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID)
                                );
                                HeaderUtil.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER + " " + lastmileSecurityToken);
                                log.debug("lastMileSecurityToken: " + lastmileSecurityToken);
                            } else {
                                log.debug("----------------LAST MILE SECURITY (LEGACY)-------------");
                                jumperInfoRequest.ifPresent(i -> i.setInfoScenario(true, false, false, false, false));

                                String lastmileSecurityToken = oauthTokenUtil.generateGatewayToken(jumperConfig.getEnvName(), jumperConfig.getConsumerToken(), String.valueOf(request.getMethod()), jumperConfig.getRequestPath(), lmsIssuer);

                                HeaderUtil.addHeader(exchange, Constants.HEADER_LASTMILE_SECURITY_TOKEN, Constants.BEARER + " " + lastmileSecurityToken);
                                log.debug("lastMileSecurityToken: " + lastmileSecurityToken);
                            }

                        }

                    } else {
                            /** GW-2-GW MESH TOKEN GENERATION **/
                            log.debug("----------------GATEWAY MESH-------------");
                            jumperInfoRequest.ifPresent(i -> i.setInfoScenario(false, false, true, false, false));

                            TokenInfo meshTokenInfo = oauthTokenUtil.getInternalMeshAccessToken(jumperConfig);

                            // set gw and consumer tokens correctly
                            HeaderUtil.addHeader(exchange, Constants.HEADER_AUTHORIZATION, "Bearer " + meshTokenInfo.getAccessToken());
                            HeaderUtil.addHeader(exchange, Constants.HEADER_CONSUMER_TOKEN, jumperConfig.getConsumerToken());

                            checkForSpaceZone(exchange, jumperConfig.getConsumerOriginZone(), jumperConfig.getConsumerToken());

                    }

                }

                HeaderUtil.addHeader(exchange, Constants.HEADER_X_ORIGIN_STARGATE, jumperConfig.getConsumerOriginStargate());
                HeaderUtil.addHeader(exchange, Constants.HEADER_X_ORIGIN_ZONE, jumperConfig.getConsumerOriginZone());
                HeaderUtil.rewriteXForwardedHeader(exchange, jumperConfig);

                jumperInfoRequest.ifPresent(infoRequest -> {
                    IncomingRequest incReq = createIncomingRequest(jumperConfig, request);
                    infoRequest.setIncomingRequest(incReq);
                    log.info("logging request: {}", value("jumperInfo", infoRequest));
                });

                addTracingInfo(request);

            });
            return chain.filter(exchange);
        }, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
    }

    private Optional<JumperInfoRequest> initializeJumperInfoRequest(JumperConfig jumperConfig) {

        if (log.isInfoEnabled()) {
            JumperInfoRequest jumperInfoRequest = new JumperInfoRequest();
            jumperInfoRequest.setEnvironment(jumperConfig.getEnvName());
            return Optional.of(jumperInfoRequest);
        }

        return Optional.empty();
    }

    private IncomingRequest createIncomingRequest(JumperConfig jumperConfig, ServerHttpRequest request) {
        IncomingRequest incReq = new IncomingRequest();
        incReq.setBasePath(jumperConfig.getApiBasePath());
        incReq.setHost(jumperConfig.getRemoteApiUrl());
        incReq.setMethod(String.valueOf(request.getMethod()));
        incReq.setResource(jumperConfig.getRoutingPath());

        HashMap<String, String> logEntries = new HashMap<>();
        logEntries.put("Thread name", Thread.currentThread().getName());

        incReq.setLogEntries(logEntries);
        return incReq;
    }

    private void calculateRoutingStuff(ServerHttpRequest request, ServerWebExchange exchange, String routePathPrefix, JumperConfig jumperConfig) {

        try {
            URI uri = request.getURI();
            String queryParameterPart = uri.getRawQuery();
            String fragmentPart = uri.getFragment();
            String routingPath = uri.getRawPath().replaceFirst("^" + routePathPrefix, "");

            String requestPath = jumperConfig.getApiBasePath() + routingPath;

            if (Objects.nonNull(queryParameterPart)) {
                routingPath += "?" + queryParameterPart;
            }

            if (Objects.nonNull(fragmentPart)) {
                routingPath += "#" + fragmentPart;
            }

            String finalApiUrl = jumperConfig.getRemoteApiUrl().replaceAll("/$", "") + routingPath;

            // store final destination url to exchange
            log.debug("Routing set to: " + finalApiUrl);
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(finalApiUrl));

            // add calculated stuff to jumperConfig
            jumperConfig.setRequestPath(requestPath);
            jumperConfig.setRoutingPath(routingPath);

        } catch (URISyntaxException e) {
            throw new RuntimeException("can not construct URL from " + request.getURI(), e);
        }
    }

    private void clientCredentialsFlow_legacy(ServerWebExchange exchange, JumperConfig jc) {

        String clientScope = "";
        String consumer = jc.getConsumer();
        String tokenEndpoint = jc.getExternalTokenEndpoint();
        String clientId = jc.getClientId();
        String clientSecret = jc.getClientSecret();
        String xSpacegateClientId = jc.getXSpacegateClientId();
        String xSpacegateClientSecret = jc.getXSpacegateClientSecret();
        String xSpacegateScope = jc.getXSpacegateScope();


        // set clientId
        if (StringUtils.isNotBlank(xSpacegateClientId)) {
            log.debug( "Using SubscriberClientId {} from xSpacegateClientId-Header", xSpacegateClientId);
            clientId = xSpacegateClientId;
            HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_ID);

        } else if (Objects.nonNull(jc.getOauth())
                && jc.getOauth().containsKey(consumer)
                && StringUtils.isNotBlank(jc.getOauth().get(consumer).getClientId())) {

            log.debug( "Using SubscriberClientId {} from JumperConfig", jc.getOauth().get(consumer).getClientId());
            clientId = jc.getOauth().get(consumer).getClientId();

        } else {
            log.debug( "Using default ProviderClientId {}", clientId);
        }

        // set clientSecret
        if (Objects.nonNull(xSpacegateClientSecret)) {
            log.debug( "Using SubscriberClientSecret from xSpacegateClientSecret-Header");
            clientSecret = xSpacegateClientSecret;
            HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);

        } else if (Objects.nonNull(jc.getOauth())
                && jc.getOauth().containsKey(consumer)
                && StringUtils.isNotBlank(jc.getOauth().get(consumer).getClientSecret())) {
            log.debug( "Using SubscriberClientSecret from JumperConfig");
            clientSecret = jc.getOauth().get(consumer).getClientSecret();

        } else {
            log.debug( "Using default ProviderClientSecret");
        }

        // set scope
        if (Objects.nonNull(xSpacegateScope)) {
            log.debug( "Using Scope from xSpacegateScope-Header");
            clientScope = xSpacegateScope;
            HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_SCOPE);

        } else if (Objects.nonNull(jc.getOauth())
                && jc.getOauth().containsKey(consumer)
                && StringUtils.isNotBlank(jc.getOauth().get(consumer).getScopes())) {
            clientScope = jc.getOauth().get(consumer).getScopes();

        } else {
            log.debug("Using default Provider scope");
            if (StringUtils.isNotBlank(jc.getScopes())) {
                clientScope = jc.getScopes();
            }
        }

        log.debug( "Get token for consumer: {} with clientId: {}", consumer, clientId);
        if ( Objects.nonNull(clientId) && Objects.nonNull(clientSecret)) {
            TokenInfo tokenInfo = oauthTokenUtil.getAccessToken(tokenEndpoint, clientId, clientSecret, clientScope, consumer);
            HeaderUtil.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER+" "+tokenInfo.getAccessToken());

        } else {
            log.warn( "not specified oauth config credentials for consumer: {}", consumer);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing oauth config credentials for consumer " + consumer);
        }
    }

    private void checkForSpaceZone(ServerWebExchange exchange, String zone, String token ) {
        if (zone != null && Constants.SPACE_ZONES.contains(zone)) {
            HeaderUtil.addHeader(exchange, Constants.HEADER_X_SPACEGATE_TOKEN, token);
        }
    }

    private String getSecurityScopes(JumperConfig jumperConfig){

        String consumer = jumperConfig.getConsumer();

        if (Objects.nonNull(jumperConfig.getOauth()) && jumperConfig.getOauth().containsKey(consumer)) {
            return jumperConfig.getOauth().get(consumer).getScopes();
        }

        return null;
    }

    private void addTracingInfo(ServerHttpRequest request) {

        String xTardisTraceId = HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_TARDIS_TRACE_ID);
        String contentLength = HeaderUtil.getLastValueFromHeaderField(request, "Content-Length");

        Span incomingRequestSpan = tracer.currentSpan();
        incomingRequestSpan.name("Incoming Request");

        //todo would prefer to set NA for this (chunked transfer?) scenario
        incomingRequestSpan.tag("message.size", Objects.requireNonNullElse(contentLength, "0"));

        if ( xTardisTraceId != null) {
            incomingRequestSpan.tag( Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
        }

        incomingRequestSpan.remoteServiceName(applicationName);
        incomingRequestSpan.event("jrqf");
    }

    @AllArgsConstructor
    @Getter
    public static class Config extends AbstractGatewayFilterFactory.NameConfig {
        private String routePathPrefix;
    }
}
