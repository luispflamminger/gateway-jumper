package jumper.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OutgoingRequest extends Request {

    private Map<String, String> zuulHeader;

}
