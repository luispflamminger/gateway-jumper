package jumper.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import jumper.autoevent.AutoEvent;
import jumper.autoevent.AutoEventService;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AutoEventResponseFilter extends AbstractGatewayFilterFactory<AutoEventResponseFilter.Config> {

    @Value( "${horizon.publishEventUrl}")
    private String publishEventUrl;

    @Autowired
    AutoEventService aes;


    /**
     * At Order "NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1" we have the response in cachedResponseBodyObject
     * It is the Order of ModifyResponseGatewayFilter.
     * As we have to work with the response message, we are attaching to this filter with -2
     */
    public static final int AUTO_EVENT_RESPONSE_FILTER_ORDER = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;

    public AutoEventResponseFilter()  {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new OrderedGatewayFilter((exchange, chain) -> {

            //try to store jc now as on response phase is not available
            //use jc passed with exchange
            //JumperConfig jc = JumperConfig.parseConfigFrom( exchange.getRequest());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {

                //ServerHttpRequest request = exchange.getRequest();
                JumperConfig jc = JumperConfig.parseConfigFrom(exchange);
                if(aes.isListenerMatched(jc))
                {
                    RouteListener listener = jc.getRouteListener().get( jc.getConsumer());

                    String responseBody = exchange.getAttribute("cachedResponseBodyObject");
                    log.debug("Response: payload={}", responseBody);

                    // Create Event with additional information
                    AutoEvent eventRespMsg = aes.createEvent(jc, exchange, exchange.getResponse(), listener, responseBody);

                    // publish event (route to local Horizon)
                    aes.publishEvent(eventRespMsg, publishEventUrl, jc, exchange);
                }

            }));
        }, AUTO_EVENT_RESPONSE_FILTER_ORDER);
    }

    public static class Config {
    }
}
