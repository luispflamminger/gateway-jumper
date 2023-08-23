package jumper.filter;

import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.spectre.SpectreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SpectreResponseFilter extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

    private final SpectreService spectreService;

    /**
     * At Order "NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1" we have the response in cachedResponseBodyObject
     * It is the Order of ModifyResponseGatewayFilter.
     * As we have to work with the response message, we are attaching to this filter with -2
     */
    public static final int AUTO_EVENT_RESPONSE_FILTER_ORDER = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;

    public SpectreResponseFilter(SpectreService spectreService)  {
        super(AbstractGatewayFilterFactory.NameConfig.class);
        this.spectreService = spectreService;
    }

    @Override
    public GatewayFilter apply(AbstractGatewayFilterFactory.NameConfig config) {
        return new OrderedGatewayFilter((exchange, chain) -> {

            //try to store jc now as on response phase it is not available
            //use jc passed with exchange
            //JumperConfig jc = JumperConfig.parseConfigFrom( exchange.getRequest());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {

                String responseBody = exchange.getAttribute("cachedResponseBodyObject");
                log.debug("Response: status={}, headers={}, payload={}",exchange.getResponse().getStatusCode().value(), exchange.getResponse().getHeaders().toSingleValueMap(), responseBody);


                //ServerHttpRequest request = exchange.getRequest();
                JumperConfig jc = JumperConfig.parseConfigFrom(exchange);
                if(spectreService.isListenerMatched(jc))
                {
                    RouteListener listener = jc.getRouteListener().get( jc.getConsumer());
/*
                    // Create Event with additional information
                    Spectre eventRespMsg = aes.createEvent(jc, exchange, exchange.getResponse(), listener, responseBody);

                    // publish event (route to local Horizon)
                    aes.publishEvent(eventRespMsg, jc);
 */
                    spectreService.handleEvent(jc, exchange, exchange.getResponse(), listener, responseBody);
                }

            }));
        }, AUTO_EVENT_RESPONSE_FILTER_ORDER);
    }

}
