package jumper;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jumper.filter.*;
import jumper.spectre.SpectreBodyRewrite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class Application {

    @Value( "${horizon.publishEventUrl}")
    private String publishEventUrl;

    @Value("${CUSTOM_CIPHERS:}")
    List<String> custom_ciphers;

    @Autowired
    private SpectreBodyRewrite spectreBodyRewrite;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RouteLocator proxyRoute(RouteLocatorBuilder builder, Tracer tracer, RequestFilter requestFilter, RemoveHeaderFilter removeHeader, ResponseFilter responseFilter, SpectreRequestFilter spectreRequestFilter, SpectreResponseFilter spectreResponseFilter, RequestTransformationFilter requestTransformationFilter, ResponseTransformationFilter responseTransformationFilter, SetSpectreRoutingFilter setSpectreRoutingFilter) {
        return builder.routes()
                .route("jumper_route", p -> p
                        .path("/proxy/**")
                        .filters(f -> f
                                .filter(requestFilter.apply(new RequestFilter.Config(true, true, tracer)))
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
                                .filter(responseFilter.apply(c -> c.setTracer(tracer)))
                        )
                        .uri("no://op"))
                .route("listener_route", p -> p
                        .path("/listener/**")
                        .filters(f -> f
                                        .filter(requestFilter.apply(new RequestFilter.Config(true, true, tracer)))
                                        .filter(requestTransformationFilter)
                                        .filter(responseTransformationFilter)
                                        .filter(spectreRequestFilter.apply(new SpectreRequestFilter.Config()))
                                        .filter(spectreResponseFilter.apply(new SpectreResponseFilter.Config()))
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
                                        .filter(responseFilter.apply(c -> c.setTracer(tracer)))
                        )
                        .uri("no://op"))
                .route("auto_event_route_post", p -> p
                        .path("/autoevent/**").and().method(HttpMethod.POST)
                        .filters(f -> f
                                .modifyRequestBody(String.class, String.class,
                                        spectreBodyRewrite)
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(setSpectreRoutingFilter.apply())
                        )
                        .uri(publishEventUrl))
                .route("auto_event_route_head", p -> p
                        .path("/autoevent/**").and().method(HttpMethod.HEAD)
                        .filters(f -> f
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(setSpectreRoutingFilter.apply())
                        )
                        .uri(publishEventUrl))
                .build();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        http.httpBasic().disable()
                .formLogin().disable()
                .csrf().disable()
                .logout().disable()
//                .headers().cache().disable()
        ;

        return http.build();
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

            SslContext s = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .protocols("TLSv1.2","TLSv1.3")
                    .sslProvider(SslProvider.JDK)
                    .ciphers((Iterable<String>) Stream.concat(dt_ciphers.stream(),
                            custom_ciphers.stream())
                            .distinct().collect(Collectors.toList())
                    )
                    .build();

            return httpClient -> httpClient
                    .secure(t -> t.sslContext(s));

        }
        catch (SSLException e){
            e.printStackTrace();
        }

        return httpClient -> httpClient;
    }

    
    @Bean
    public WebClient createWebClient() throws SSLException {
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

}

