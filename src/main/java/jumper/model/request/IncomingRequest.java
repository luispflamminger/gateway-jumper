package jumper.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class IncomingRequest extends Request {
	
	HashMap<String, String> logEntries;
	
}
