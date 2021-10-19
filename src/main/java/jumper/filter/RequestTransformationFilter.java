package jumper.filter;

import jumper.utilities.RequestBodyRewrite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RequestTransformationFilter implements  GatewayFilter, Ordered{
    @Autowired private ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter;
    @Autowired private RequestBodyRewrite requestBodyRewrite;


    public static final int REQUEST_TRANSFORM_FILTER_ORDER = RemoveHeaderFilter.REMOVE_HEADER_FILTER_ORDER +1;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return modifyRequestBodyFilter
                    .apply(
                            new ModifyRequestBodyGatewayFilterFactory.Config()
                                    .setRewriteFunction(String.class, String.class, requestBodyRewrite))
                    .filter(exchange, chain);
        }

    public int getOrder() {
        return REQUEST_TRANSFORM_FILTER_ORDER;
    }

}

