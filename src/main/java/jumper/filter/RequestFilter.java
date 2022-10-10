package jumper.filter;

import brave.Span;
import brave.Tracer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.model.request.OutgoingRequest;
import jumper.utilities.OauthTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    public static final int REQUEST_FILTER_ORDER = RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

    @Autowired
    Tracer tracer;

    @Autowired
    OauthTokenUtil oauthTokenUtil;

    public RequestFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter((exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();

            String client_scope = "";

            //String routing_path = request.getURI().toString().replaceFirst(".*?:\\d+", "");
            String token_endpoint = getLastValueFromHeaderField( request, Constants.HEADER_TOKEN_ENDPOINT);
            String tif_remote_issuer = getLastValueFromHeaderField( request, Constants.HEADER_ISSUER);
            String tif_clientID = getLastValueFromHeaderField( request, Constants.HEADER_CLIENT_ID);
            String tif_clientSecret = getLastValueFromHeaderField( request, Constants.HEADER_CLIENT_SECRET);
            String consumerToken = request.getHeaders().getFirst( Constants.HEADER_AUTHORIZATION);
            String api_base_path = getLastValueFromHeaderField( request, Constants.HEADER_API_BASE_PATH);
            String access_token_forwarding = getLastValueFromHeaderField( request, Constants.HEADER_ACCESS_TOKEN_FORWARDING);
            String realmName = getLastValueFromHeaderField( request, Constants.HEADER_REALM);

            if( StringUtils.isBlank(realmName))
            {
                realmName = Constants.DEFAULT_REALM;
            }

            String envName = getLastValueFromHeaderField( request, Constants.HEADER_ENVIRONMENT);

            //String api_resource = request.getPath().value();
            //String requestPath = api_base_path + api_resource;
            String routing_path;
            String requestPath = api_base_path;
            String remote_api_url = getLastValueFromHeaderField( request, Constants.HEADER_REMOTE_API_URL);
            String lastmileSecurityToken = null;

            String xSpacegateClientId = request.getHeaders().getFirst( Constants.HEADER_X_SPACEGATE_CLIENT_ID);
            String xSpacegateClientSecret = request.getHeaders().getFirst( Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);
            String xSpacegateScope = request.getHeaders().getFirst( Constants.HEADER_X_SPACEGATE_SCOPE);

            String jumper_config_Base64 = getLastValueFromHeaderField( request, Constants.HEADER_JUMPER_CONFIG);

            String consumerTokenWithoutSignature  = OauthTokenUtil.getTokenWithoutSignature( consumerToken);
            Jwt<Header, Claims> consumerTokenclaims = OauthTokenUtil.getAllClaimsFromToken( consumerTokenWithoutSignature);
            String consumer = consumerTokenclaims.getBody().get( "clientId", String.class);
            String consumerOriginStargate = consumerTokenclaims.getBody().get( "originStargate", String.class);
            String consumerOriginZone = consumerTokenclaims.getBody().get( "originZone", String.class);

            // jumper config
            JumperConfig jc = null;
            if (StringUtils.isNotBlank(jumper_config_Base64))
            {
                jc = JumperConfig.fromBase64( jumper_config_Base64);
                jc.fillWithLegacyHeaders( request); // TODO: remove as soon we have completely shifted to json_config
            }
            else
            {
                jc = new JumperConfig();
                jc.fillWithLegacyHeaders( request);
            } // TODO: remove as soon we have completely shifted to json_config

            log.debug( "JumperConfig encodedAsBase64: {}", JumperConfig.toBase64( jc));
            log.debug( "JumperConfig decoded: {}", jc.toString());

            //store enhanced jumper_config for usage in AutoEventFilters
            exchange.getAttributes().put(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toBase64( jc));

            // Pre-processing
            if (config.isPreLogger()) {
                log.debug("Pre GatewayFilter logging");
            }

            JumperInfoRequest jumperInfoRequest = new JumperInfoRequest();
            jumperInfoRequest.setEnvironment( envName);

            try {
                URI _uri = request.getURI();
                String _query = _uri.getRawQuery();
                String _fragment = _uri.getFragment();
                //String routing_path = _uri.getPath().replaceFirst("^/$","");
                //String routing_path = _uri.getRawPath().replaceFirst("^/$","");
                routing_path = _uri.getRawPath().replaceFirst("^/(proxy|listener)", ""); //for token should be also decoded
                requestPath += routing_path;
                if (_query != null) routing_path = routing_path  + "?" + _query;
                if (_fragment != null) routing_path = routing_path + "#" + _fragment;

                String finalApiUrl = remote_api_url.replaceAll("/$", "") + routing_path;

                log.debug("Routing set to: " + finalApiUrl);

                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(finalApiUrl));
            } catch (URISyntaxException e) {
                 throw new RuntimeException("TardisException", e);//todo create proper fallback
            }

            if( remote_api_url != null && !remote_api_url.startsWith( Constants.LOCALHOST_ISSUER_SERVICE))
            {
                if( tif_remote_issuer == null)
                {
                    /** LAST MILE SECURITY TOKEN GENERATION **/


                    String lmsIssuer = localIssuerUrl+"/"+realmName;

                    // Egress
                    if( token_endpoint != null)
                    {
                        log.debug( "----------------EXTERNAL AUTHORIZATION-------------");
                        jumperInfoRequest.setLastMileSecurity( false);
                        jumperInfoRequest.setLastMileSecurityEnhanced( false);
                        jumperInfoRequest.setMeshActivated( false);
                        jumperInfoRequest.setExternalAuthorization( true);

                        log.debug( "Remote TokenEndpoint is set to: %s", token_endpoint);
                        log.debug( "Get token from external idp");

                        if( xSpacegateClientId != null && !xSpacegateClientId.isBlank())
                        {
                            log.debug( "Using SubscriberClientId {} from xSpacegateClientId-Header", xSpacegateClientId);
                            tif_clientID = xSpacegateClientId;
                            removeHeader(exchange, chain, Constants.HEADER_X_SPACEGATE_CLIENT_ID);
                        }
                        else if( jc.getOauth() != null && jc.getOauth().containsKey( consumer) && jc.getOauth().get( consumer).getClientId() != null && !jc.getOauth().get( consumer).getClientId().isBlank())
                        {
                            log.debug( "Using SubscriberClientId {} from JumperConfig", jc.getOauth().get( consumer).getClientId());
                            tif_clientID = jc.getOauth().get( consumer).getClientId();
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
                            removeHeader(exchange, chain, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);
                        }
                        else if( jc.getOauth() != null && jc.getOauth().containsKey( consumer) && jc.getOauth().get( consumer).getClientSecret() != null && !jc.getOauth().get( consumer).getClientSecret().isBlank())
                        {
                            log.debug( "Using SubscriberClientSecret from JumperConfig");
                            tif_clientSecret = jc.getOauth().get( consumer).getClientSecret();
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
                            removeHeader(exchange, chain, Constants.HEADER_X_SPACEGATE_SCOPE);
                        }
                        else if( jc.getOauth() != null && jc.getOauth().containsKey( consumer) && jc.getOauth().get( consumer).getScopes() != null && !jc.getOauth().get( consumer).getScopes().isBlank())
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
                            TokenInfo tokenInfo = oauthTokenUtil.getAccessToken( token_endpoint, tif_clientID, tif_clientSecret, client_scope, consumer);
                            addHeader(exchange, chain, Constants.HEADER_AUTHORIZATION, Constants.BEARER+" "+tokenInfo.getAccessToken());
                        }
                        else
                        {
                            log.info( "no specified oauth config credentials for consumer: {}", consumer);
                        }

                    }
                    else if( access_token_forwarding != null && access_token_forwarding.equals( "false"))
                    {
                        log.debug( "----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                        jumperInfoRequest.setLastMileSecurity( true);
                        jumperInfoRequest.setLastMileSecurityEnhanced( true);
                        jumperInfoRequest.setMeshActivated( false);
                        jumperInfoRequest.setExternalAuthorization( false);

                        log.debug( "Generating OneToken...");

                        lastmileSecurityToken = OauthTokenUtil.generateExtGatewayToken( envName,
                                consumerToken,
                                request.getMethod().toString(),
                                requestPath,
                                lmsIssuer,
                                setSecurityScopes(jc, consumer),
                                request.getHeaders().getFirst(Constants.HEADER_X_PUBLISHER_ID)
                        );
                        addHeader(exchange, chain, Constants.HEADER_AUTHORIZATION, Constants.BEARER+" "+lastmileSecurityToken);
                    }
                    else
                    {
                        log.debug( "----------------LAST MILE SECURITY (LEGACY)-------------");
                        jumperInfoRequest.setLastMileSecurity( true);
                        jumperInfoRequest.setLastMileSecurityEnhanced( false);
                        jumperInfoRequest.setMeshActivated( false);
                        jumperInfoRequest.setExternalAuthorization( false);

                        log.debug( "Generating GatewayToken");

                        lastmileSecurityToken = OauthTokenUtil.generateGatewayToken( envName, consumerToken, request.getMethod().toString(), requestPath, lmsIssuer);
                        addHeader(exchange, chain, Constants.HEADER_LASTMILE_SECURITY_TOKEN, Constants.BEARER+" "+lastmileSecurityToken);
                        log.debug( "lastMileSecurityToken: "+lastmileSecurityToken);
                    }

                }
                else
                {
                    /** GW-2-GW MESH TOKEN GENERATION **/
                    log.debug( "----------------GATEWAY MESH-------------");

                    jumperInfoRequest.setLastMileSecurity( false);
                    jumperInfoRequest.setLastMileSecurityEnhanced( false);
                    jumperInfoRequest.setMeshActivated( true);
                    jumperInfoRequest.setExternalAuthorization( false);

                    tif_remote_issuer = tif_remote_issuer + Constants.ISSUER_SUFFIX;

                    TokenInfo meshTokenInfo = oauthTokenUtil.getAccessToken( tif_remote_issuer, tif_clientID, tif_clientSecret);

                    // set gw and consumer tokens correctly
                    addHeader(exchange, chain, Constants.HEADER_AUTHORIZATION, "Bearer "+meshTokenInfo.getAccessToken());
                    addHeader(exchange, chain, Constants.HEADER_CONSUMER_TOKEN, consumerToken);

                    checkForSpaceZone(exchange, chain, consumerOriginZone, consumerToken);

                }

            }

            addHeader(exchange, chain, Constants.HEADER_X_ORIGIN_STARGATE, consumerOriginStargate);
            addHeader(exchange, chain, Constants.HEADER_X_ORIGIN_ZONE, consumerOriginZone);

            if (consumerOriginStargate != null) {
                String hostStargate = "";
                try {
                    URL url = new URL(consumerOriginStargate);
                    hostStargate = url.getHost();
                } catch (MalformedURLException e) {
                    log.error(e.getMessage(), e);
                }
                addHeader(exchange, chain, Constants.HEADER_X_FORWARDED_HOST, hostStargate);
            }

            rewriteXForwardedHeader(exchange, chain);


            IncomingRequest incReq = new IncomingRequest();
            incReq.setBasePath(api_base_path);
            incReq.setHost(remote_api_url);
            incReq.setMethod(request.getMethodValue());
            incReq.setResource(routing_path);

            OutgoingRequest outgoingRequest = new OutgoingRequest();
            outgoingRequest.setHost( remote_api_url);
            outgoingRequest.setBasePath( null);
            outgoingRequest.setResource( routing_path);
            outgoingRequest.setMethod( request.getMethod().toString());

            HashMap<String, String> logEntries = new HashMap<String, String>();
            logEntries.put("Thread name", Thread.currentThread().getName());

            incReq.setLogEntries(logEntries);
            jumperInfoRequest.setIncomingRequest(incReq);

            log.info( "logging request", value( "jumperInfo", jumperInfoRequest));

            addTracing(request, api_base_path, envName, consumer, consumerOriginStargate);

            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() -> {
                        // Post-processing

                        // do something with the response


                        if (config.isPostLogger()) {
                            log.debug("Post GatewayFilter logging");
                        }
                    }));
        }, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
    }

    private String getLastValueFromHeaderField(ServerHttpRequest request, String headerName) {
        return request.getHeaders().getValuesAsList(headerName)
                .stream()
                .reduce((first, last) -> last)
                .orElse(null);
    }

    private void checkForSpaceZone(ServerWebExchange exchange, GatewayFilterChain chain, String zone, String token ) {
        if(zone != null && zone.equals(Constants.SPACE)) {
            addHeader(exchange, chain, Constants.HEADER_X_SPACEGATE_TOKEN, token);
        }
    }
    //todo workaround for getting token, preferable to make it non-blocking
    private void assureGatewayToken(ServerWebExchange exchange, JumperConfig jc){
        Route r = exchange.getAttribute("org.springframework.cloud.gateway.support.ServerWebExchangeUtils.gatewayRoute");
        if (r.getId().equals("listener_route") && jc.getGatewayClient() != null){
            String local_issuer = jc.getGatewayClient().getIssuer() + Constants.ISSUER_SUFFIX;
            oauthTokenUtil.getAccessToken(local_issuer, jc.getGatewayClient().getId(), jc.getGatewayClient().getSecret());
        }
    }

    private void addTracing(ServerHttpRequest request, String api_base_path, String envName, String consumer, String consumerOriginStargate) {
        // Tracing - Start

        String xB3TraceId = request.getHeaders().getFirst( Constants.HEADER_X_B3_TRACE_ID);
        String xTardisTraceId = request.getHeaders().getFirst( Constants.HEADER_X_TARDIS_TRACE_ID);
        String xBusinessContext = request.getHeaders().getFirst( Constants.HEADER_X_BUSINESS_CONTEXT);
        String xRequestId = request.getHeaders().getFirst( Constants.HEADER_X_REQUEST_ID);
        String xCorrelationId = request.getHeaders().getFirst( Constants.HEADER_X_CORRELATION_ID);
        Long contentLength = request.getHeaders().getContentLength();
        String publisherId = request.getHeaders().getFirst(Constants.HEADER_X_PUBLISHER_ID);

        Span newSpan = this.tracer.nextSpan().name( "Request Filter");
        try( Tracer.SpanInScope ws = this.tracer.withSpanInScope( newSpan.start()))
        {

            if( xTardisTraceId != null){

                newSpan.tag( Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
            }

            if( consumerOriginStargate != null)
            {

                newSpan.tag( "origin-stargate", consumerOriginStargate);
            }

            if (contentLength == null || contentLength.toString().equals( "-1"))
            {

                newSpan.tag( "message.size", "0");
            }
            else
            {

                newSpan.tag( "message.size", contentLength.toString());
            }


            if( envName != null)
            {

                newSpan.tag( "environment.info", envName);
            }


            if( xB3TraceId != null)
            {

                newSpan.tag( Constants.HEADER_X_B3_TRACE_ID, xB3TraceId);
            }

            if( xBusinessContext != null)
            {

                newSpan.tag( Constants.HEADER_X_BUSINESS_CONTEXT, xBusinessContext);
            }

            if( xRequestId != null)
            {

                newSpan.tag( Constants.HEADER_X_REQUEST_ID, xRequestId);
            }

            if( xCorrelationId != null)
            {

                newSpan.tag( Constants.HEADER_X_CORRELATION_ID, xCorrelationId);
            }

            //callback
            if (publisherId != null){
                newSpan.tag("publisher", publisherId);

                String subscriptionId = request.getHeaders().getFirst(Constants.HEADER_X_SUBSCRIPTION_ID);
                if (subscriptionId != null){
                    newSpan.tag("subscription-id", subscriptionId);
                }
            }
            //not callback, assume request-response
            else {

                if (api_base_path != null) {

                    newSpan.tag("peer.service", api_base_path.substring(1).replace("/", "-"));
                }

                if (consumer != null) {

                    newSpan.tag("consumer", consumer);
                }
            }
        }
        finally
        {

            newSpan.finish();
        }
        // Tracing - End
    }

    private void rewriteXForwardedHeader( ServerWebExchange exchange, GatewayFilterChain chain) {

//        String normalizedForwardedHost = "";

//        ServerHttpRequest request = exchange.getRequest();

//        String forwardedHost = request.getHeaders().getFirst( Constants.HEADER_X_FORWARDED_HOST);
//
//        /**
//         * As we have to gateways (kong and spring cloud gateway) in place, the forwarded host is added twice to X-Forwarded-Host header
//         */
//        String[] splittedForwardedHost = StringUtils.split(forwardedHost, ",");
//        if(splittedForwardedHost != null && splittedForwardedHost.length >= 1) {
//            normalizedForwardedHost = StringUtils.removeEnd(splittedForwardedHost[0], ":");
//        }

//        addHeader(exchange, chain, Constants.HEADER_X_FORWARDED_HOST, normalizedForwardedHost);
        addHeader(exchange, chain, Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT);
        addHeader(exchange, chain, Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);


    }

    private void addHeader(ServerWebExchange exchange, GatewayFilterChain chain, String headerName, String headerValue) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(headerName, headerValue)
                .build();
        ServerWebExchange exchange1 = exchange.mutate().request(request).build();
        chain.filter(exchange1);
    }

    private void removeHeader(ServerWebExchange exchange, GatewayFilterChain chain, String headerName){
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(httpHeaders -> httpHeaders.remove(headerName))
                .build();
        ServerWebExchange exchange1 = exchange.mutate().request(request).build();
        chain.filter(exchange1);
    }

    private String setSecurityScopes(JumperConfig jumperConfig, String consumer){
        //temporarily we will support both oauth structures
        //todo remove oauthSecurity
        if (jumperConfig.getOauthSecurity() != null) {
            if (jumperConfig.getOauthSecurity().containsKey(consumer)) {
                return jumperConfig.getOauthSecurity().get(consumer).getScopes();
            }
        }
        if (jumperConfig.getOauth() != null && jumperConfig.getOauth().containsKey(consumer)){
            return jumperConfig.getOauth().get(consumer).getScopes();
        }
        return null;
    }

    public static class Config {
        private boolean preLogger;
        private boolean postLogger;

        public Config(boolean preLogger, boolean postLogger) {
            this.preLogger = preLogger;
            this.postLogger = postLogger;
        }

        public boolean isPreLogger() {
            return preLogger;
        }

        public void setPreLogger(boolean preLogger) {
            this.preLogger = preLogger;
        }

        public boolean isPostLogger() {
            return postLogger;
        }

        public void setPostLogger(boolean postLogger) {
            this.postLogger = postLogger;
        }
    }
}
