package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import jumper.Constants;
import jumper.service.HeaderUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutingConfig {
  private List<JumperConfig> jumperConfigList;

  public static List<JumperConfig> parseConfigFromHeader(ServerHttpRequest request) {

    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isNotBlank(routingConfigBase64)) {
      return RoutingConfig.fromBase64(routingConfigBase64);
    }

    return List.of();
  }

  // todo merge method
  public static List<JumperConfig> fromBase64(String jsonConfigBase64) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));

    TypeReference<List<JumperConfig>> typeRef = new TypeReference<>() {};
    try {
      return new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
          .readValue(decodedJson, typeRef);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    assert false : "routing config can not be decoded";
    return null;
  }
}
