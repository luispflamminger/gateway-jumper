package jumper.utilities;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class RequestBodyRewrite implements RewriteFunction<byte[], byte[]> {

    @Autowired
    JumperUtil jumperUtil;

    @Override
    public Publisher<byte[]> apply(ServerWebExchange exchange, byte[] originalBody) {
        if (originalBody != null) {
            exchange.getAttributes().put("cachedRequestBodyObject", jumperUtil.getBodyForContentType(exchange.getRequest().getHeaders().getContentType(), originalBody));
            return Mono.just(originalBody);
        } else {
            return Mono.empty();
        }
    }
}
