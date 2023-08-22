package jumper;

import jumper.filter.RemoveHeaderFilter;
import jumper.filter.RequestFilter;
import jumper.filter.RequestTransformationFilter;
import jumper.filter.ResponseFilter;
import jumper.filter.ResponseTransformationFilter;
import jumper.filter.SpectreRoutingFilter;
import jumper.filter.SpectreRequestFilter;
import jumper.filter.SpectreResponseFilter;
import jumper.spectre.SpectreBodyRewrite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;


@SpringBootApplication
public class Application {

    @Value("${horizon.publishEventUrl}")
    private String publishEventUrl;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public RouteLocator proxyRoute(RouteLocatorBuilder builder,
                                   Tracer tracer,
                                   RequestFilter requestFilter,
                                   RemoveHeaderFilter removeHeader,
                                   ResponseFilter responseFilter,
                                   SpectreRequestFilter spectreRequestFilter,
                                   SpectreResponseFilter spectreResponseFilter,
                                   RequestTransformationFilter requestTransformationFilter,
                                   ResponseTransformationFilter responseTransformationFilter,
                                   SpectreRoutingFilter spectreRoutingFilter,
                                   SpectreBodyRewrite spectreBodyRewrite) {

        return builder.routes()


                .route("jumper_route", p -> p
                        .path(Constants.PROXY_ROOT_PATH_PREFIX + "/**")
                        .filters(filterSpec -> filterSpec
                                .filter(requestFilter.apply(new RequestFilter.Config(true, true, tracer, Constants.PROXY_ROOT_PATH_PREFIX)))

                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_JUMPER_CONFIG)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_TOKEN_ENDPOINT)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_REMOTE_API_URL)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_ISSUER)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_CLIENT_ID)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_CLIENT_SECRET)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_API_BASE_PATH)))
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
                        .path(Constants.LISTENER_ROOT_PATH_PREFIX + "/**")
                        .filters(filterSpec -> filterSpec
                                .filter(requestFilter.apply(new RequestFilter.Config(true, true, tracer, Constants.LISTENER_ROOT_PATH_PREFIX)))

                                .filter(requestTransformationFilter)
                                .filter(responseTransformationFilter)
                                .filter(spectreRequestFilter.apply(new SpectreRequestFilter.Config()))
                                .filter(spectreResponseFilter.apply(new SpectreResponseFilter.Config()))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_JUMPER_CONFIG)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_TOKEN_ENDPOINT)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_REMOTE_API_URL)))
                                .filter(removeHeader.apply(c -> c.setName(Constants.HEADER_ISSUER)))
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
                        .path(Constants.AUTOEVENT_ROOT_PATH_PREFIX + "/**").and().method(HttpMethod.POST)
                        .filters(filterSpec -> filterSpec
                                .modifyRequestBody(String.class, String.class, spectreBodyRewrite)
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(spectreRoutingFilter.apply())
                        )
                        .uri(publishEventUrl))


                .route("auto_event_route_head", p -> p
                        .path(Constants.AUTOEVENT_ROOT_PATH_PREFIX + "/**").and().method(HttpMethod.HEAD)
                        .filters(filterSpec -> filterSpec
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(spectreRoutingFilter.apply())
                        )
                        .uri(publishEventUrl))


                .build();
    }





}

