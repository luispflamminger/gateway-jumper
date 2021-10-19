package jumper.utilities;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class JumperUtil {

    private String body;

    public String getRequestBody(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Flux<DataBuffer> dataBufferFlux = request.getBody().doOnNext(dataBuffer -> {
            try {
                Channels.newChannel(baos).write(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                body = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                log.info("Request: payload={}", body);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    baos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        return body;
    }
}
