package jumper.filter;


import jumper.utilities.ResponseBodyRewrite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ResponseTransformationFilter implements  GatewayFilter, Ordered{
    @Autowired private ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilter;
    @Autowired private ResponseBodyRewrite responseBodyRewrite;


    public static final int RESPONSE_TRANSFORM_FILTER_ORDER = SpectreResponseFilter.AUTO_EVENT_RESPONSE_FILTER_ORDER-1;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return modifyResponseBodyFilter
                    .apply(
                            new ModifyResponseBodyGatewayFilterFactory.Config()
                                    .setRewriteFunction(byte[].class, byte[].class, responseBodyRewrite))
                    .filter(exchange, chain);
        }

    public int getOrder() {
        return RESPONSE_TRANSFORM_FILTER_ORDER;
    }

}

