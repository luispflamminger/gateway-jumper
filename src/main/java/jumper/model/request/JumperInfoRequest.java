package jumper.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map.Entry;
import java.util.Set;

@Getter
@Setter
public class JumperInfoRequest {

	private boolean meshActivated;
    private boolean lastMileSecurity;
    private boolean lastMileSecurityEnhanced;
    private boolean externalAuthorization;
    
//    private String correlationId;
    private String environment;
//    private String zone;
//    private String clientId;

    private IncomingRequest incomingRequest;
    private OutgoingRequest outgoingRequest;
    
    @Override
    public String toString()
    {
    	StringBuilder sb = new StringBuilder();
    	sb.append("IncomingRequest");
    	sb.append(System.getProperty("line.separator"));
    	
    	sb.append("HTTP-Method: "+incomingRequest.getMethod());
    	sb.append(System.getProperty("line.separator"));
    	
    	sb.append("Host: "+incomingRequest.getHost());
    	sb.append(System.getProperty("line.separator"));
    	
    	sb.append("BasePath: "+incomingRequest.getBasePath());
    	sb.append(System.getProperty("line.separator"));
    	
    	sb.append("Resource: "+incomingRequest.getResource());
    	sb.append(System.getProperty("line.separator"));
    	
    	Set<Entry<String,String>> entrySet = incomingRequest.getLogEntries().entrySet();
    	for (Entry<String, String> entry : entrySet) {
			sb.append(entry.getKey()+": "+entry.getValue());
			sb.append(System.getProperty("line.separator"));
		}
    	
    	
		return sb.toString();
    	
    }

}
