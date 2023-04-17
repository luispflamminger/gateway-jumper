package jumper.filter;

import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.spectre.SpectreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpectreRequestFilter extends AbstractGatewayFilterFactory<SpectreRequestFilter.Config> {

    @Autowired
    SpectreService aes;

    public static final int AUTO_EVENT_REQUEST_FILTER_ORDER = RequestTransformationFilter.REQUEST_TRANSFORM_FILTER_ORDER+1;

    public SpectreRequestFilter()  {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter((exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();

            String requestBody = exchange.getAttribute("cachedRequestBodyObject");
            log.debug("Request: headers={}, payload={}", request.getHeaders().toSingleValueMap(), requestBody);

            //JumperConfig jc = JumperConfig.parseConfigFrom( request);
            JumperConfig jc = JumperConfig.parseConfigFrom( exchange);
            if(!aes.isListenerMatched(jc))
            {
                return chain.filter(exchange.mutate().request(request).build());
            }

            RouteListener listener = jc.getRouteListener().get( jc.getConsumer());
/*
            // Create Event with additional information
            Spectre eventReqMsg = aes.createEvent(jc, exchange, exchange.getRequest(), listener, requestBody);

            // publish event (route to local Horizon)
            aes.publishEvent(eventReqMsg, jc);
*/
            aes.handleEvent(jc, exchange, exchange.getRequest(), listener, requestBody);
            return chain.filter(exchange);

        }, AUTO_EVENT_REQUEST_FILTER_ORDER);
    }

    public static class Config {
    }
}
