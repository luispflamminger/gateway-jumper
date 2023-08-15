package jumper.spectre;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreData;
import jumper.model.config.SpectreKind;
import jumper.utilities.OauthTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
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
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpectreService
{
    private final Tracer tracer;
    private final CurrentTraceContext currentTraceContext;


    @Value( "${jumper.stargate.url}")
    private String stargateUrl;

    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    @Value( "${horizon.publishEventUrl}")
    private String publishEventUrl;

    WebClient webClient = WebClient.create();

    public boolean isAnyListenerPresent( JumperConfig jc) {
        return jc.getRouteListener() != null && !jc.getRouteListener().isEmpty();
    }

    public boolean isListenerMatched( JumperConfig jc) {

        if (!isAnyListenerPresent(jc)) {
            return false;
        }

        String consumer = jc.getConsumer();
        return Objects.nonNull(jc.getRouteListener().get( consumer));
    }

    public void handleEvent(JumperConfig jc, ServerWebExchange exchange, Object http, RouteListener listener, String payload){
        WebFluxSleuthOperators.withSpanInScope(tracer, currentTraceContext, exchange, () -> {
            publishEvent(createEvent( jc,  exchange,  http,  listener,  payload), jc) ;
        });
    }

    private Spectre createEvent(JumperConfig jc, ServerWebExchange exchange, Object http, RouteListener listener, String payload) {

        ServerHttpRequest rq = exchange.getRequest();
        ServerHttpResponse rs = exchange.getResponse();

        Spectre event = new Spectre();
        event.setSpecversion( "1.0");
        event.setSource( stargateUrl);
        event.setId( UUID.randomUUID());
        event.setDatacontenttype( "application/json");
        event.setType( "de.telekom.ei.listener");

        SpectreData data = null;
        String spanName = "Spectre request";
        if( http instanceof ServerHttpRequest)
        {
            data = new SpectreData();
            Map<String,String> httpHeaders = new HashMap<>();
            httpHeaders.putAll(rq.getHeaders().toSingleValueMap());
            httpHeaders.replace(Constants.HEADER_AUTHORIZATION, jc.getConsumerToken());
            httpHeaders.remove(Constants.HEADER_CONSUMER_TOKEN);
            data.setHeader( httpHeaders);
            data.setKind( SpectreKind.REQUEST.toString());
            data.setPayload(parsePayload(rq.getHeaders().getContentType(), payload));
            data.setParameters(rq.getQueryParams().toSingleValueMap());
        }

        if( http instanceof ServerHttpResponse)
        {
            spanName = ("Spectre response");

            data = new SpectreData();
            Map<String,String> httpHeaders = new HashMap<>();
            httpHeaders.putAll(rs.getHeaders().toSingleValueMap());
            httpHeaders.put(Constants.HEADER_X_TARDIS_TRACE_ID, rq.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID));
            data.setHeader( httpHeaders);
            data.setKind( SpectreKind.RESPONSE.toString());
            data.setPayload(parsePayload(rs.getHeaders().getContentType(), payload));
            data.setStatus( rs.getStatusCode().value());
        }
        data.setConsumer( jc.getConsumer());
        data.setIssue( listener.getIssue());
        data.setProvider( listener.getServiceOwner());
        data.setMethod( rq.getMethod().toString());

        event.setData( data);

        String finalSpanName = spanName;

            Span newSpan = this.tracer.nextSpan().name(finalSpanName).start();
            tracer.withSpan(newSpan);

            event.setSpanId(newSpan.context().spanId());

            newSpan.tag("spectre.issue",
                    listener.getIssue());
            newSpan.tag("spectre.provider",
                    listener.getServiceOwner());
            newSpan.tag("spectre.consumer",
                    jc.getConsumer());
            //newSpan.tag("span.kind", "client");
            newSpan.end();

        return event;
    }


    private void publishEvent(Spectre event, JumperConfig jc) {
        String eventJson = null;
        try {
            eventJson = new ObjectMapper().writeValueAsString(event);
        } catch (JsonProcessingException e1) {
            e1.printStackTrace();
        }

        //determine environment for local issuer and routing path on qa
        //default fallback value
        String envName = Constants.DEFAULT_REALM;

        //for real route environment header is set, so also available within jc
        if (jc.getGatewayClient().getIssuer() != null) {
            envName = jc.getGatewayClient().getIssuer().replaceFirst(".*realms\\/", "");
        }
        //on proxy route we need to use token
        else {
            if (jc.getConsumerToken() != null) {
                envName = OauthTokenUtil.getClaimFromToken(jc.getConsumerToken(), "iss").replaceFirst(".*realms\\/", "");
            }
        }

        publishEventMono(publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName),
                eventJson,
                OauthTokenUtil.generateGatewayTokenForPublisher(localIssuerUrl + "/" + envName), event.getSpanId()
        ).subscribe();

    }

    private Mono<Void> publishEventMono(String url, String eventJson, String token, String spanId) {
        final Mono<Void> responseMono = webClient.post()
                .uri(url)
                .headers(httpHeaders -> {

                    httpHeaders.setBearerAuth(token);

                    //pass tracing info from request to spectre, maybe also new client span should be created
                    Span currentSpan = tracer.currentSpan();
                    if (currentSpan != null) {
                        httpHeaders.set(Constants.HEADER_X_B3_TRACE_ID, currentSpan.context().traceId());
                        httpHeaders.set(Constants.HEADER_X_B3_SPAN_ID, spanId);
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

        return responseMono.then(Mono.defer(Mono::empty));
    }

    private static void logDebugResponse(Logger log, ClientResponse response) {
        if (log.isDebugEnabled()) {
            log.debug("Response headers: {}", response.headers().asHttpHeaders());
            response.bodyToMono(String.class)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(body -> log.debug("Response body: {}", body));
        }
    }

    private Object parsePayload (MediaType mediaType, String payload){
        if (payload == null) {
            return null;
        }

        if (mediaType != null && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            log.debug("json compatible content-type, will try to parse as json payload");
            try{
                return new ObjectMapper().readTree(payload);
            }
            catch (JsonProcessingException e){
                e.printStackTrace();
            }
        }

        return payload;
    }
}
