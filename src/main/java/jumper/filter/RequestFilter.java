package jumper.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.BasicAuthCredentials;
import jumper.model.config.JumperConfig;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.model.request.OutgoingRequest;
import jumper.service.HeaderUtilService;
import jumper.service.OauthTokenUtilService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

    private final CurrentTraceContext currentTraceContext;
    private final Tracer tracer;
    private final OauthTokenUtilService oauthTokenUtilService;

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    @Value( "${spring.application.name}")
    private String applicationName;

    public static final int REQUEST_FILTER_ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

    public RequestFilter(CurrentTraceContext currentTraceContext, Tracer tracer, OauthTokenUtilService oauthTokenUtilService) {
        super(Config.class);
        this.currentTraceContext = currentTraceContext;
        this.tracer = tracer;
        this.oauthTokenUtilService = oauthTokenUtilService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter((exchange, chain) -> {
            WebFluxSleuthOperators.withSpanInScope(tracer, currentTraceContext, exchange, () -> {

                ServerHttpRequest request = exchange.getRequest();


                String client_scope = "";

                String token_endpoint = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_TOKEN_ENDPOINT);
                String tif_remote_issuer = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_ISSUER);
                String tif_clientID = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_ID);
                String tif_clientSecret = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SECRET);
                String consumerToken = HeaderUtilService.getFirstValueFromHeaderField(request,Constants.HEADER_AUTHORIZATION);
                String api_base_path = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_API_BASE_PATH);
                String access_token_forwarding = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_ACCESS_TOKEN_FORWARDING);
                String realmName = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_REALM);

                if (StringUtils.isBlank(realmName)) {
                    realmName = Constants.DEFAULT_REALM;
                }

                String envName = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_ENVIRONMENT);

                String routing_path;
                String requestPath = api_base_path;
                String remote_api_url = getLastValueFromHeaderField(request, Constants.HEADER_REMOTE_API_URL);
                String lastmileSecurityToken = null;

                //to prevent later nullPointer on inconsistent state from Kong
                if (remote_api_url == null){
                    throw new RuntimeException("missing mandatory header remote_api_url");
                }

                String xSpacegateClientId = HeaderUtilService.getFirstValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID);
                String xSpacegateClientSecret = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);
                String xSpacegateScope = HeaderUtilService.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_SCOPE);

                String consumerTokenWithoutSignature = OauthTokenUtilService.getTokenWithoutSignature(consumerToken);
                Jwt<Header, Claims> consumerTokenclaims = OauthTokenUtilService.getAllClaimsFromToken(consumerTokenWithoutSignature);
                String consumer = consumerTokenclaims.getBody().get("clientId", String.class);
                String consumerOriginStargate = consumerTokenclaims.getBody().get("originStargate", String.class);
                String consumerOriginZone = consumerTokenclaims.getBody().get("originZone", String.class);

                JumperConfig jumperConfig = JumperConfig.parseConfigFrom(request);
                jumperConfig.setConsumer(consumer);

                log.debug("JumperConfig encodedAsBase64: {}", JumperConfig.toBase64(jumperConfig));
                log.debug("JumperConfig decoded: {}", jumperConfig);

                //store enhanced jumper_config for usage in Spectre
                exchange.getAttributes().put(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toBase64(jumperConfig));

                JumperInfoRequest jumperInfoRequest = null;
                if (isInfoLogLevelEnabled()){
                    jumperInfoRequest = new JumperInfoRequest();
                    jumperInfoRequest.setEnvironment(envName);
                }

                String finalApiUrl = "";
                try {
                    URI _uri = request.getURI();
                    String _query = _uri.getRawQuery();
                    String _fragment = _uri.getFragment();
                    routing_path = _uri.getRawPath().replaceFirst("^" + config.getRoutePathPrefix(), ""); //for token should be also decoded
                    if (requestPath != null) requestPath += routing_path;
                    if (_query != null) routing_path = routing_path + "?" + _query;
                    if (_fragment != null) routing_path = routing_path + "#" + _fragment;

                    finalApiUrl = remote_api_url.replaceAll("/$", "") + routing_path;

                    log.debug("Routing set to: " + finalApiUrl);

                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(finalApiUrl));
                } catch (URISyntaxException e) {
                    throw new RuntimeException("can not construct URL from " + finalApiUrl, e);
                }

                if (remote_api_url != null && !remote_api_url.startsWith(Constants.LOCALHOST_ISSUER_SERVICE)) {
                    if (tif_remote_issuer == null) {
                        /** ALL NON MESH SCENARIOS **/

                        if (jumperConfig.getBasicAuth() != null && (jumperConfig.getBasicAuth().containsKey(consumer) || jumperConfig.getBasicAuth().containsKey(Constants.BASIC_AUTH_PROVIDER_KEY))) {
                            log.debug("----------------BASIC AUTH HEADER-------------");
                            if (isInfoLogLevelEnabled()) {
                                jumperInfoRequest.setLastMileSecurity(false);
                                jumperInfoRequest.setLastMileSecurityEnhanced(false);
                                jumperInfoRequest.setMeshActivated(false);
                                jumperInfoRequest.setExternalAuthorization(false);
                                jumperInfoRequest.setBasicAuth(true);
                            }

                            BasicAuthCredentials basicAuthCredentials = jumperConfig.getBasicAuth().containsKey(consumer) ? jumperConfig.getBasicAuth().get(consumer) : jumperConfig.getBasicAuth().get(Constants.BASIC_AUTH_PROVIDER_KEY);
                            HeaderUtilService.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BASIC + " " + OauthTokenUtilService.encodeBasicAuth(basicAuthCredentials.getUsername(), basicAuthCredentials.getPassword()));
                        } else {


                            String lmsIssuer = localIssuerUrl + "/" + realmName;

                            // Egress
                            if (token_endpoint != null) {
                                log.debug("----------------EXTERNAL AUTHORIZATION-------------");
                                if (isInfoLogLevelEnabled()) {
                                    jumperInfoRequest.setLastMileSecurity(false);
                                    jumperInfoRequest.setLastMileSecurityEnhanced(false);
                                    jumperInfoRequest.setMeshActivated(false);
                                    jumperInfoRequest.setExternalAuthorization(true);
                                    jumperInfoRequest.setBasicAuth(false);
                                }

                                log.debug("Remote TokenEndpoint is set to: %s", token_endpoint);

                                if (jumperConfig.getOauth() != null && jumperConfig.getOauth().containsKey(consumer) && jumperConfig.getOauth().get(consumer).getGrantType() != null && !jumperConfig.getOauth().get(consumer).getGrantType().isBlank()) {
                                    TokenInfo tokenInfo = oauthTokenUtilService.getAccessToken(token_endpoint, jumperConfig.getOauth().get(consumer), consumer);
                                    HeaderUtilService.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER + " " + tokenInfo.getAccessToken());
                                } else {
                                    clientCredentialsFlow_legacy(exchange, chain, client_scope, token_endpoint, tif_clientID, tif_clientSecret, xSpacegateClientId, xSpacegateClientSecret, xSpacegateScope, consumer, jumperConfig);
                                }


                            } else if (access_token_forwarding != null && access_token_forwarding.equals("false")) {
                                log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                                if (isInfoLogLevelEnabled()) {
                                    jumperInfoRequest.setLastMileSecurity(true);
                                    jumperInfoRequest.setLastMileSecurityEnhanced(true);
                                    jumperInfoRequest.setMeshActivated(false);
                                    jumperInfoRequest.setExternalAuthorization(false);
                                    jumperInfoRequest.setBasicAuth(false);
                                }

                                lastmileSecurityToken = oauthTokenUtilService.generateExtGatewayToken(envName,
                                        consumerToken,
                                        request.getMethod().toString(),
                                        requestPath,
                                        lmsIssuer,
                                        setSecurityScopes(jumperConfig, consumer),
                                        request.getHeaders().getFirst(Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                                        request.getHeaders().getFirst(Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID)
                                );
                                HeaderUtilService.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER + " " + lastmileSecurityToken);
                                log.debug("lastMileSecurityToken: " + lastmileSecurityToken);
                            } else {
                                log.debug("----------------LAST MILE SECURITY (LEGACY)-------------");
                                if (isInfoLogLevelEnabled()) {
                                    jumperInfoRequest.setLastMileSecurity(true);
                                    jumperInfoRequest.setLastMileSecurityEnhanced(false);
                                    jumperInfoRequest.setMeshActivated(false);
                                    jumperInfoRequest.setExternalAuthorization(false);
                                    jumperInfoRequest.setBasicAuth(false);
                                }

                                lastmileSecurityToken = oauthTokenUtilService.generateGatewayToken(envName, consumerToken, request.getMethod().toString(), requestPath, lmsIssuer);

                                HeaderUtilService.addHeader(exchange, Constants.HEADER_LASTMILE_SECURITY_TOKEN, Constants.BEARER + " " + lastmileSecurityToken);
                                log.debug("lastMileSecurityToken: " + lastmileSecurityToken);
                            }

                        }

                    } else{
                            /** GW-2-GW MESH TOKEN GENERATION **/
                            log.debug("----------------GATEWAY MESH-------------");

                            if (isInfoLogLevelEnabled()) {
                                jumperInfoRequest.setLastMileSecurity(false);
                                jumperInfoRequest.setLastMileSecurityEnhanced(false);
                                jumperInfoRequest.setMeshActivated(true);
                                jumperInfoRequest.setExternalAuthorization(false);
                                jumperInfoRequest.setBasicAuth(false);
                            }

                            tif_remote_issuer = tif_remote_issuer + Constants.ISSUER_SUFFIX;

                            TokenInfo meshTokenInfo = oauthTokenUtilService.getAccessToken(tif_remote_issuer, tif_clientID, tif_clientSecret);

                            // set gw and consumer tokens correctly
                            HeaderUtilService.addHeader(exchange, Constants.HEADER_AUTHORIZATION, "Bearer " + meshTokenInfo.getAccessToken());
                            HeaderUtilService.addHeader(exchange, Constants.HEADER_CONSUMER_TOKEN, consumerToken);

                            checkForSpaceZone(exchange, chain, consumerOriginZone, consumerToken);

                        }

                }

                HeaderUtilService.addHeader(exchange, Constants.HEADER_X_ORIGIN_STARGATE, consumerOriginStargate);
                HeaderUtilService.addHeader(exchange, Constants.HEADER_X_ORIGIN_ZONE, consumerOriginZone);

                if (consumerOriginStargate != null) {
                    String hostStargate = "";
                    try {
                        URL url = new URL(consumerOriginStargate);
                        hostStargate = url.getHost();
                    } catch (MalformedURLException e) {
                        log.error(e.getMessage(), e);
                    }
                    HeaderUtilService.addHeader(exchange, Constants.HEADER_X_FORWARDED_HOST, hostStargate);
                }

                HeaderUtilService.rewriteXForwardedHeader(exchange);

                if(isInfoLogLevelEnabled()) {
                    IncomingRequest incReq = new IncomingRequest();
                    incReq.setBasePath(api_base_path);
                    incReq.setHost(remote_api_url);
                    incReq.setMethod(request.getMethodValue());
                    incReq.setResource(routing_path);

                    OutgoingRequest outgoingRequest = new OutgoingRequest();
                    outgoingRequest.setHost(remote_api_url);
                    outgoingRequest.setBasePath(null);
                    outgoingRequest.setResource(routing_path);
                    outgoingRequest.setMethod(request.getMethod().toString());

                    HashMap<String, String> logEntries = new HashMap<String, String>();
                    logEntries.put("Thread name", Thread.currentThread().getName());

                    incReq.setLogEntries(logEntries);
                    jumperInfoRequest.setIncomingRequest(incReq);

                    log.info("logging request: {}", value("jumperInfo", jumperInfoRequest));
                }

                addTracingInfo(request);

            });
            return chain.filter(exchange);
        }, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
    }

    private void clientCredentialsFlow_legacy(ServerWebExchange exchange, GatewayFilterChain chain, String client_scope, String token_endpoint, String tif_clientID, String tif_clientSecret, String xSpacegateClientId, String xSpacegateClientSecret, String xSpacegateScope, String consumer, JumperConfig jc) {
        if( xSpacegateClientId != null && !xSpacegateClientId.isBlank())
        {
            log.debug( "Using SubscriberClientId {} from xSpacegateClientId-Header", xSpacegateClientId);
            tif_clientID = xSpacegateClientId;
            HeaderUtilService.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_ID);
        }
        else if( jc.getOauth() != null && jc.getOauth().containsKey(consumer) && jc.getOauth().get(consumer).getClientId() != null && !jc.getOauth().get(consumer).getClientId().isBlank())
        {
            log.debug( "Using SubscriberClientId {} from JumperConfig", jc.getOauth().get(consumer).getClientId());
            tif_clientID = jc.getOauth().get(consumer).getClientId();
        }
        else
        {
            log.debug( "Using default ProviderClientId {}", tif_clientID);
        }

        // set clientSecret
        if( xSpacegateClientSecret != null)
        {
            log.debug( "Using SubscriberClientSecret from xSpacegateClientSecret-Header");
            tif_clientSecret = xSpacegateClientSecret;
            HeaderUtilService.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);
        }
        else if( jc.getOauth() != null && jc.getOauth().containsKey(consumer) && jc.getOauth().get(consumer).getClientSecret() != null && !jc.getOauth().get(consumer).getClientSecret().isBlank())
        {
            log.debug( "Using SubscriberClientSecret from JumperConfig");
            tif_clientSecret = jc.getOauth().get(consumer).getClientSecret();
        }
        else
        {
            log.debug( "Using default ProviderClientSecret");
        }

        // set scope
        if( xSpacegateScope != null)
        {
            log.debug( "Using Scope from xSpacegateScope-Header");
            client_scope = xSpacegateScope;
            HeaderUtilService.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_SCOPE);
        }
        else if( jc.getOauth() != null && jc.getOauth().containsKey(consumer) && jc.getOauth().get(consumer).getScopes() != null && !jc.getOauth().get(consumer).getScopes().isBlank())
        {
            client_scope = jc.getOauth().get(consumer).getScopes();
        }
        else
        {
            log.debug("Using default Provider scope");
            if(jc.getScopes() != null && !jc.getScopes().isEmpty())
            {
                client_scope = jc.getScopes();
            }
        }


        log.debug( "Get token for consumer: {} with clientId: {}", consumer, tif_clientID);
        if( tif_clientID != null && tif_clientSecret != null)
        {
            TokenInfo tokenInfo = oauthTokenUtilService.getAccessToken(token_endpoint, tif_clientID, tif_clientSecret, client_scope, consumer);
            HeaderUtilService.addHeader(exchange, Constants.HEADER_AUTHORIZATION, Constants.BEARER+" "+tokenInfo.getAccessToken());

        }
        else
        {
            log.warn( "not specified oauth config credentials for consumer: {}", consumer);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing oauth config credentials for consumer " + consumer);
        }
    }

    private String getLastValueFromHeaderField(ServerHttpRequest request, String headerName) {
        return request.getHeaders().getValuesAsList(headerName)
                .stream()
                .reduce((first, last) -> last)
                .orElse(null);
    }

    private void checkForSpaceZone(ServerWebExchange exchange, GatewayFilterChain chain, String zone, String token ) {
        if(zone != null && Constants.SPACE_ZONES.contains(zone)) {
            HeaderUtilService.addHeader(exchange, Constants.HEADER_X_SPACEGATE_TOKEN, token);
        }
    }

    private void addTracingInfo(ServerHttpRequest request) {

        String xTardisTraceId = request.getHeaders().getFirst( Constants.HEADER_X_TARDIS_TRACE_ID);

        String contentLength = request.getHeaders().getFirst("Content-Length");

        Span incomingRequestSpan = tracer.currentSpan();
        incomingRequestSpan.name("Incoming Request");

        if (contentLength == null) {
            incomingRequestSpan.tag("message.size", "0");//todo would prefer to set NA for this (chunked transfer?) scenario
        } else {
            incomingRequestSpan.tag("message.size", contentLength);
        }

        if( xTardisTraceId != null){
            incomingRequestSpan.tag( Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
        }

        incomingRequestSpan.remoteServiceName(applicationName);
        incomingRequestSpan.event("jrqf");
    }

    private String setSecurityScopes(JumperConfig jumperConfig, String consumer){
        if (jumperConfig.getOauth() != null && jumperConfig.getOauth().containsKey(consumer)){
            return jumperConfig.getOauth().get(consumer).getScopes();
        }
        return null;
    }

    private boolean isInfoLogLevelEnabled(){
        return log.isInfoEnabled();
    }

    @AllArgsConstructor
    @Getter
    public static class Config extends AbstractGatewayFilterFactory.NameConfig {
        private String routePathPrefix;
    }
}
