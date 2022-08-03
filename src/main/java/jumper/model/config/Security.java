package jumper.model.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
public class Security {
    private HashMap<String, List<String>> scopes;
}
