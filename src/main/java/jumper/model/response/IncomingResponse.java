package jumper.model.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IncomingResponse {

    private String host;
    private String path;
    private Integer httpStatusCode;

    List<String> originHeaderResponse;

}
