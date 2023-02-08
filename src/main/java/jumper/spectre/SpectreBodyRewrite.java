package jumper.spectre;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.model.config.Spectre;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Slf4j
@Service
public class SpectreBodyRewrite implements RewriteFunction<String, String> {
    public final String listenerQueryParam = "listener";

    @Override
    public Publisher<String> apply(ServerWebExchange exchange, String body) {
        MultiValueMap<String, String> params = exchange.getRequest().getQueryParams();

        String id = null;
        if(params.containsKey(listenerQueryParam))
        {
            id = params.getFirst(listenerQueryParam);
        }

        log.debug("Spectre: payload={}", body);

        // adjust EventType.<applicationId>
        Spectre event = adjustEventType(body, id);

        String eventJson = null;
        try
        {
            eventJson = new ObjectMapper().writeValueAsString( event);
        }
        catch( JsonProcessingException e1)
        {
            e1.printStackTrace();
        }
        log.debug("Spectre: adjusted={}", eventJson);
        return Mono.just(eventJson);
    }

    private Spectre adjustEventType(String body, String id) {
        Spectre event = null;

        try
        {
            event = new ObjectMapper().readValue( body, Spectre.class);
            event.setType( event.getType()+"."+id);
        }
        catch( IOException e)
        {
            e.printStackTrace();
        }

        return event;
    }
}
