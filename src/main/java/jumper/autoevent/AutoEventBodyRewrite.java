package jumper.autoevent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class AutoEventBodyRewrite implements RewriteFunction<String, String> {
    public final String listenerQueryParam = "listener";

    @Override
    public Publisher<String> apply(ServerWebExchange exchange, String body) {
        MultiValueMap<String, String> params = exchange.getRequest().getQueryParams();

        String id = null;
        if(params.containsKey(listenerQueryParam))
        {
            id = params.getFirst(listenerQueryParam);
        }

        log.debug("Autoevent: payload={}", body);

        // adjust EventType.<applicationId>
        AutoEvent event = adjustEventType(body, id);

        String eventJson = null;
        try
        {
            eventJson = new ObjectMapper().writeValueAsString( event);
        }
        catch( JsonProcessingException e1)
        {
            e1.printStackTrace();
        }
        log.debug("Autoevent: adjusted={}", eventJson);
        return Mono.just(eventJson);
    }

    private AutoEvent adjustEventType( String body, String id) {
        AutoEvent event = null;

        try
        {
            event = new ObjectMapper().readValue( body, AutoEvent.class);
            event.setType( event.getType()+"."+id);
        }
        catch( IOException e)
        {
            e.printStackTrace();
        }

        return event;
    }
}
