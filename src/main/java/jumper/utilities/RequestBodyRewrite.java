package jumper.utilities;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class RequestBodyRewrite implements RewriteFunction<String, String> {

    @Override
    public Publisher<String> apply(ServerWebExchange exchange, String originalBody) {
        if (originalBody != null) {
            exchange.getAttributes().put("cachedRequestBodyObject", originalBody);
            log.debug("storing: {}", originalBody);
            return Mono.just(originalBody);
        } else {
            return Mono.empty();
        }
    }
}
