package jumper.autoevent;

import brave.Span;
import brave.Tracer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.utilities.OauthTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
public class AutoEventService
{
    @Autowired
    OauthTokenUtil oauthTokenUtil;

    @Autowired
    Tracer tracer;

    @Value( "${jumper.stargate.url}")
    private String stargateUrl;

    private TokenInfo gwToken;

    WebClient webClient = WebClient.create();

    public boolean isAnyListenerPresent( JumperConfig jc) {

        if( jc.getRouteListener() != null && !jc.getRouteListener().isEmpty())
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public boolean isListenerMatched( JumperConfig jc) {

        if( !isAnyListenerPresent( jc))
        {
            return false;
        }

        String consumer = jc.getConsumer();
        return jc.getRouteListener().containsKey( consumer) && jc.getRouteListener().get( consumer) != null;

    }

    public AutoEvent createEvent(JumperConfig jc, ServerWebExchange exchange, Object http, RouteListener listener, String payload) {

        ServerHttpRequest rq = exchange.getRequest();
        ServerHttpResponse rs = exchange.getResponse();

        AutoEvent event = new AutoEvent();
        event.setSpecversion( "1.0");
        event.setSource( stargateUrl);
        event.setId( UUID.randomUUID());
        event.setDatacontenttype( "application/json");
        event.setType( "de.telekom.ei.listener");

        AutoEventData data = null;
        if( http instanceof ServerHttpRequest)
        {
            data = new AutoEventData();
            Map<String,String> httpHeaders = new HashMap<>();
            httpHeaders.putAll(rq.getHeaders().toSingleValueMap());
            httpHeaders.replace(Constants.HEADER_AUTHORIZATION, jc.getConsumerToken());
            httpHeaders.remove(Constants.HEADER_CONSUMER_TOKEN);
            data.setHeader( httpHeaders);
            data.setKind( AutoEventKind.REQUEST.toString());
            data.setPayload(parsePayload(rq.getHeaders().getContentType(), payload));
            data.setParameters(rq.getQueryParams().toSingleValueMap());
        }

        if( http instanceof ServerHttpResponse)
        {
            data = new AutoEventData();
            Map<String,String> httpHeaders = new HashMap<>();
            httpHeaders.putAll(rs.getHeaders().toSingleValueMap());
            httpHeaders.put(Constants.HEADER_X_TARDIS_TRACE_ID, rq.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID));
            data.setHeader( httpHeaders);
            data.setKind( AutoEventKind.RESPONSE.toString());
            data.setPayload(parsePayload(rs.getHeaders().getContentType(), payload));
            data.setStatus( rs.getStatusCode().value());
        }
        data.setConsumer( jc.getConsumer());
        data.setIssue( listener.getIssue());
        data.setProvider( listener.getServiceOwner());
        data.setMethod( rq.getMethod().toString());

        event.setData( data);
        return event;
    }

    /***
     * publish event (route to local Horizon)
     *
     * @param event
     */
    public void publishEvent( AutoEvent event, String url, JumperConfig jc, ServerWebExchange exchange ) {
        String eventJson = null;
        try
        {
            eventJson = new ObjectMapper().writeValueAsString( event);
        }
        catch( JsonProcessingException e1)
        {
            e1.printStackTrace();
        }

        if(jc != null) {
            // get token with GatewayClient
            String local_issuer = jc.getGatewayClient().getIssuer() + Constants.ISSUER_SUFFIX;
            gwToken = oauthTokenUtil.getAccessToken(local_issuer, jc.getGatewayClient().getId(), jc.getGatewayClient().getSecret(), true, null);

            log.debug("will publish event: {}", eventJson);
            if (gwToken != null) {
                publishEventMono(url, eventJson).subscribe();
            }
            else{
                //just log error as we do not want to affect real message processing
                log.error("did not get token for client: {} from {}, will not publish event", jc.getGatewayClient().getId(), local_issuer);
            }
        }



/*
        ClientResponse horizonResp = webClient.post()
                .uri(url)
                //.headers(HttpHeaders.AUTHORIZATION, Constants.BEARER + " " + gwToken.getAccessToken())
                .headers(new Consumer<HttpHeaders>() {
                    @Override
                    public void accept(HttpHeaders httpHeaders) {
                        if(jc != null) {
                            httpHeaders.setBearerAuth(gwToken.getAccessToken());
                        }
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(eventJson))
                .exchange()
                .block();

        log.info("Horizon Response statusCode: "+horizonResp.statusCode().value());
*/
    }
    /*
    public Mono<String> publishEventMono(String url, String eventJson) {
        final Mono<String> responseMono = webClient.post()
                .uri(url)
                .headers(new Consumer<HttpHeaders>() {
                    @Override
                    public void accept(HttpHeaders httpHeaders) {
                        httpHeaders.setBearerAuth(gwToken.getAccessToken());
        }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(eventJson))
                .retrieve()
                .onStatus(status -> !HttpStatus.CREATED.equals(status),
                        response -> response.bodyToMono(String.class).map(body -> new RuntimeException(body)))
                .bodyToMono(String.class)
                .doOnSuccess(status -> {
                    log.debug("publishEventMono success" );
                })
                .onErrorMap(e -> new RuntimeException("message", e));

        return responseMono.flatMap(response -> {
            log.debug("Horizon Response: {}", response);
            return Mono.just(response);
        });
    }
*/
    public Mono<Void> publishEventMono(String url, String eventJson) {
        final Mono<Void> responseMono = webClient.post()
                .uri(url)
                .headers(new Consumer<HttpHeaders>() {
                    @Override
                    public void accept(HttpHeaders httpHeaders) {
                        httpHeaders.setBearerAuth(gwToken.getAccessToken());

                        //pass tracing info from request to autoevent, maybe also new client span should be created
                        Span currentSpan = tracer.currentSpan();
                        if (currentSpan != null) {
                            String b3 = currentSpan.context().traceIdString() + "-" + currentSpan.context().spanIdString();
                            if (currentSpan.context().sampled()) b3 += "-1";
                            else b3 += "-0";
                            if (currentSpan.context().parentIdString() != null) b3 += "-" + currentSpan.context().parentIdString();
                            log.debug("set b3 : {} to created event", b3);
                            httpHeaders.set(Constants.HEADER_B3, b3);
                        }
                    }
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(eventJson))
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    log.error("while publishing event got error status: {}", response.statusCode());
                    logDebugResponse(log, response);
                    return Mono.empty();
                })
                .onStatus(status -> !HttpStatus.CREATED.equals(status), response -> {
                    log.warn("while publishing event got unexpected status: {}", response.statusCode());
                    logDebugResponse(log, response);
                    return Mono.empty();
                })
                .bodyToMono(Void.class)
                .doOnSuccess(status -> {
                    log.debug("publishEventMono success" );
                });

        return responseMono.flatMap(response -> Mono.empty());
    }

    private static void logDebugResponse(Logger log, ClientResponse response) {
        if (log.isDebugEnabled()) {
            //log.debug("Response status: {}", response.statusCode());
            log.debug("Response headers: {}", response.headers().asHttpHeaders());
            response.bodyToMono(String.class)
                    .publishOn(Schedulers.elastic())
                    .subscribe(body -> log.debug("Response body: {}", body));
        }
    }

    private Object parsePayload (MediaType mediaType, String s){
        if (s == null) return s;

        if (mediaType != null && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)){
            log.debug("json compatible content-type, try to use json payload");
            try{
                JsonNode j = new ObjectMapper().readTree(s);
                return j;
            }
            catch (JsonProcessingException e){
                e.printStackTrace();
            }
        }

        return s;
    }
}
