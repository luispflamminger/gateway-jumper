package jumper.autoevent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

@Data
public class AutoEvent
{
    private String specversion;
    private String type; //listener.ei.telekom.de.listener
    private String source = "RouteListener";
    private UUID id;
    private String datacontenttype;
    private AutoEventData data;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String time;
}
