package jumper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jumper.autoevent.AutoEventBodyRewrite;
import jumper.filter.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import brave.http.HttpRequestParser;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

@SpringBootApplication
public class Application {

    @Value( "${horizon.publishEventUrl}")
    private String publishEventUrl;
    private  String publishEventUrlPath;

    @Value("${CUSTOM_CIPHERS:#{null}}")
    List<String> custom_ciphers;

    public final String listenerQueryParam = "listener";

    @Autowired
    private AutoEventBodyRewrite autoEventBodyRewrite;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    //todo how to normally?
    @Bean
    public void setPublishEventUrlPath(){
        try{
            URI uri = new URI(publishEventUrl);
            publishEventUrlPath = uri.getPath();
        }
        catch (URISyntaxException ex){
            ex.printStackTrace();
        }
    }

    @Bean
    public RouteLocator proxyRoute(RouteLocatorBuilder builder, RequestFilter requestFilter, RemoveHeaderFilter removeHeader, ResponseFilter responseFilter, AutoEventRequestFilter autoEventRequestFilter, AutoEventResponseFilter autoEventResponseFilter, RequestTransformationFilter requestTransformationFilter, ResponseTransformationFilter responseTransformationFilter) {
        return builder.routes()
                .route("jumper_route", p -> p
                        .path("/proxy/**")
                        .filters(f -> f
                                .rewritePath("/proxy/?(?<segment>/?.*)", "/$\\{segment}")
                                .filter(requestFilter.apply(new RequestFilter.Config(true, true)))
                                .filter(removeHeader.apply(c -> c.setName("jumper_config")))
                                .filter(removeHeader.apply(c -> c.setName("token_endpoint")))
                                .filter(removeHeader.apply(c -> c.setName("remote_api_url")))
                                .filter(removeHeader.apply(c -> c.setName("issuer")))
                                .filter(removeHeader.apply(c -> c.setName("client_id")))
                                .filter(removeHeader.apply(c -> c.setName("client_secret")))
                                .filter(removeHeader.apply(c -> c.setName("api_base_path")))
                                .filter(removeHeader.apply(c -> c.setName("x-consumer-id")))
                                .filter(removeHeader.apply(c -> c.setName("x-consumer-custom-id")))
                                .filter(removeHeader.apply(c -> c.setName("x-consumer-groups")))
                                .filter(removeHeader.apply(c -> c.setName("x-consumer-username")))
                                .filter(removeHeader.apply(c -> c.setName("x-anonymous-consumer")))
                                .filter(removeHeader.apply(c -> c.setName("x-anonymous-groups")))
                                .filter(removeHeader.apply(c -> c.setName("x-forwarded-prefix")))
                                .filter(removeHeader.apply(c -> c.setName("access_token_forwarding")))
                                .filter(responseFilter.apply(c -> c.setName("test")))
                        )
                        .uri("no://op"))
                .route("listener_route", p -> p
                        .path("/listener/**")
                        //.and().method("POST")
                        //.and().readBody(String.class, requestBody -> {return true;})
                        .filters(f -> f
                                        .rewritePath("/listener/?(?<segment>/?.*)", "/$\\{segment}")
                                        .filter(requestFilter.apply(new RequestFilter.Config(true, true)))
/*
                               .modifyResponseBody(String.class, String.class,
                                		(webExchange, originalBody) -> {
                                			if (originalBody != null) {
                                				webExchange.getAttributes().put("cachedResponseBodyObject", originalBody);
                                				return Mono.just(originalBody);
                                			} else {
                                				return Mono.empty();
                                			}
                                		})
*/
/*
                                .modifyRequestBody(String.class, String.class,
                                        (webExchange, originalBody) -> {
                                            if (originalBody != null) {
                                                webExchange.getAttributes().put("cachedRequestBodyObject", originalBody);
                                                return Mono.just(originalBody);
                                            } else {
                                                return Mono.empty();
                                            }
                                        })
*/
                                        .filter(requestTransformationFilter)
                                        .filter(responseTransformationFilter)
                                        .filter(autoEventRequestFilter.apply(new AutoEventRequestFilter.Config()))
                                        .filter(autoEventResponseFilter.apply(new AutoEventResponseFilter.Config()))
                                        .filter(removeHeader.apply(c -> c.setName("jumper_config")))
                                        .filter(removeHeader.apply(c -> c.setName("token_endpoint")))
                                        .filter(removeHeader.apply(c -> c.setName("remote_api_url")))
                                        .filter(removeHeader.apply(c -> c.setName("issuer")))
                                        .filter(removeHeader.apply(c -> c.setName("client_id")))
                                        .filter(removeHeader.apply(c -> c.setName("client_secret")))
                                        .filter(removeHeader.apply(c -> c.setName("api_base_path")))
                                        .filter(removeHeader.apply(c -> c.setName("x-consumer-id")))
                                        .filter(removeHeader.apply(c -> c.setName("x-consumer-custom-id")))
                                        .filter(removeHeader.apply(c -> c.setName("x-consumer-groups")))
                                        .filter(removeHeader.apply(c -> c.setName("x-consumer-username")))
                                        .filter(removeHeader.apply(c -> c.setName("x-anonymous-consumer")))
                                        .filter(removeHeader.apply(c -> c.setName("x-anonymous-groups")))
                                        .filter(removeHeader.apply(c -> c.setName("x-forwarded-prefix")))
                                        .filter(removeHeader.apply(c -> c.setName("access_token_forwarding")))
                                        .filter(responseFilter.apply(c -> c.setName("test")))
                        )
                        .uri("no://op"))
                .route("auto_event_route_post", p -> p
                        .path("/autoevent/**").and().method(HttpMethod.POST)
                        .filters(f -> f
                                .modifyRequestBody(String.class, String.class,
                                        autoEventBodyRewrite)
                                .rewritePath("/autoevent", publishEventUrlPath)
                                .removeRequestParameter(listenerQueryParam)
                        )
                        .uri(publishEventUrl))
                .route("auto_event_route_head", p -> p
                        .path("/autoevent/**").and().method(HttpMethod.HEAD)
                        .filters(f -> f
                                .rewritePath("/autoevent", publishEventUrlPath)
                                .removeRequestParameter(listenerQueryParam)
                        )
                        .uri(publishEventUrl))
                .build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        http.httpBasic().disable()
                .formLogin().disable()
                .csrf().disable()
                .logout().disable();


        return http.build();
    }

    //  Customize sleuth HttpServer span
    @Bean
    HttpRequestParser sleuthHttpServerRequestParser() {
        return (req, context, span) -> {
            HttpRequestParser.DEFAULT.parse(req, context, span);
            String xTardisTraceId = req.header(Constants.HEADER_X_TARDIS_TRACE_ID);
            String contentLength = req.header("Content-Length");

            span.name("Incoming Request");

            if (xTardisTraceId != null) {
                span.tag("x-tardis-traceid", xTardisTraceId);
            }

            if (contentLength == null) {

                span.tag("message.size", "0");
            } else {

                span.tag("message.size", contentLength);
            }

        };
    }

    @Bean
    public HttpClientCustomizer httpClientCustomizer() {
        try {
            List dt_ciphers =  List.of("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
                    ,"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"
                    ,"TLS_DHE_DSS_WITH_AES_256_GCM_SHA384"
                    ,"TLS_DHE_RSA_WITH_AES_256_GCM_SHA384"
                    ,"TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256"
                    ,"TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
                    ,"TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
                    //,"TLS_ECDHE_ECDSA_WITH_AES_256_CCM"
                    //,"TLS_DHE_RSA_WITH_AES_256_CCM"
                    ,"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
                    ,"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
                    ,"TLS_DHE_DSS_WITH_AES_128_GCM_SHA256"
                    ,"TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
                    //,"TLS_ECDHE_ECDSA_WITH_AES_128_CCM"
                    //,"TLS_DHE_RSA_WITH_AES_128_CCM"
                    ,"TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384"
                    ,"TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384"
                    ,"TLS_DHE_DSS_WITH_AES_256_CBC_SHA256"
                    ,"TLS_DHE_RSA_WITH_AES_256_CBC_SHA256"
                    ,"TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256"
                    ,"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"
                    ,"TLS_DHE_DSS_WITH_AES_128_CBC_SHA256"
                    ,"TLS_DHE_RSA_WITH_AES_128_CBC_SHA256"
                    ,"TLS_AES_256_GCM_SHA384"
                    ,"TLS_CHACHA20_POLY1305_SHA256"
                    ,"TLS_AES_128_GCM_SHA256"
                    //,"TLS_AES_128_CCM_SHA256"
            );
            List<String> ciphers = new LinkedList<>(dt_ciphers);
            if (custom_ciphers != null){
                for (String cipher: custom_ciphers){
                    if (!ciphers.contains(cipher)) ciphers.add(cipher);
                }
            }
            SslContext s = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    //missing
                    //.ciphers(List.of("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384"))
                    //current list + missing
                    //.ciphers(List.of("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_256_CBC_SHA", "TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384"))
                    .protocols("TLSv1.2","TLSv1.3")
                    .ciphers(ciphers)
                    .build();

            return httpClient -> httpClient
                    .secure(t -> t.sslContext(s));

        }
        catch (SSLException e){
            e.printStackTrace();
        }

        return httpClient -> httpClient;
    }


    //  Customize sleuth HttpClient span
    /*
    @Bean
    HttpRequestParser sleuthHttpClientRequestParser() {
        return (req, context, span) -> {

            String tif_remote_issuer = req.header(Constants.HEADER_ISSUER);
            String url = req.url();
            String xTardisTraceId = req.header(Constants.HEADER_X_TARDIS_TRACE_ID);
            String contentLength = req.header("Content-Length");
            String spanName = "Provider";

            if (tif_remote_issuer != null) {
                spanName = "Gateway";
            }

            if (xTardisTraceId != null) {
                span.tag("x-tardis-traceid", xTardisTraceId);
            }

            span.name("Outgoing Request: " + spanName);
            span.tag("http.method", req.method());
            span.tag("http.path", req.path());

            if (url != null) {

                span.tag("http.url", url);
            }

            if (contentLength == null) {

                span.tag("message.size", "0");
            } else {

                span.tag("message.size", contentLength);
            }
        };
    }

     */
}

