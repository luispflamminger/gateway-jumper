package jumper.model.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public abstract class Request {

    private String host;
    private String basePath;
    private String resource;
    private String method;

    private Map<String, String> originHeader;

}
