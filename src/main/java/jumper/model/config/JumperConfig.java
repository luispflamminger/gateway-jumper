package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.Constants;
import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.Base64;
import java.util.HashMap;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JumperConfig {

    private HashMap<String, OauthCredentials> oauth;
    private HashMap<String, BasicAuthCredentials> basicAuth;
    private HashMap<String, RouteListener> routeListener;
    private GatewayClient gatewayClient;

    String scopes;
    String consumerToken;
    String api_base_path;
    String consumer;
    String api_resource;

    /*
        String token_endpoint;
        String tif_remote_issuer;
        String tif_clientID;
        String tif_clientSecret;
        String access_token_forwarding;
        String realmName;
        String envName;
        String xB3TraceId;
        String xTardisTraceId;
        String xBusinessContext;
        String xRequestId;
        String xCorrelationId;
        String requestPath;
        String remote_api_url;
        String debugHeader;
    */
    @JsonIgnore
    public static String toBase64(JumperConfig jc) {
        String jsonConfigBase64 = null;
        try {
            String decodedJson = new ObjectMapper().writeValueAsString(jc);
            jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return jsonConfigBase64;
    }

    @JsonIgnore
    public static JumperConfig fromBase64(String jsonConfigBase64) {
        String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
        JumperConfig jc = null;
        try {
            jc = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(decodedJson, JumperConfig.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return jc;
    }

    @JsonIgnore
    public void fillWithLegacyHeaders(ServerHttpRequest request) {
        scopes = request.getHeaders().getFirst(Constants.HEADER_CLIENT_SCOPES);
        consumerToken = request.getHeaders().getFirst(Constants.HEADER_AUTHORIZATION);
        api_base_path = request.getHeaders().getFirst(Constants.HEADER_API_BASE_PATH);
        api_resource = request.getPath().value();

/*
        token_endpoint = request.getHeaders().getFirst( Constants.HEADER_TOKEN_ENDPOINT);
        tif_remote_issuer = request.getHeaders().getFirst( Constants.HEADER_ISSUER);
        tif_clientID = request.getHeaders().getFirst( Constants.HEADER_CLIENT_ID);
        tif_clientSecret = request.getHeaders().getFirst( Constants.HEADER_CLIENT_SECRET);
        access_token_forwarding = request.getHeaders().getFirst( Constants.HEADER_ACCESS_TOKEN_FORWARDING);
        realmName = request.getHeaders().getFirst( Constants.HEADER_REALM);
        envName = request.getHeaders().getFirst( Constants.HEADER_ENVIRONMENT);

        xB3TraceId = request.getHeaders().getFirst( Constants.HEADER_X_B3_TRACE_ID);
        xTardisTraceId = request.getHeaders().getFirst( Constants.HEADER_X_TARDIS_TRACE_ID);
        xBusinessContext = request.getHeaders().getFirst( Constants.HEADER_X_BUSINESS_CONTEXT);
        xRequestId = request.getHeaders().getFirst( Constants.HEADER_X_BUSINESS_CONTEXT);
        xCorrelationId = request.getHeaders().getFirst( Constants.HEADER_X_CORRELATION_ID);

        requestPath = api_base_path + api_resource;
        remote_api_url = request.getHeaders().getFirst( Constants.HEADER_REMOTE_API_URL);

        debugHeader = request.getHeaders().getFirst( Constants.HEADER_DEBUG_RESPONSE_HEADER);
 */
    }


    @JsonIgnore
    public static JumperConfig parseConfigFrom(ServerWebExchange exchange) {
        String jumperConfigBase64 = exchange.getAttribute(Constants.HEADER_JUMPER_CONFIG);
        if (jumperConfigBase64 != null && !jumperConfigBase64.isEmpty()) {
            return JumperConfig.fromBase64(jumperConfigBase64);
        } else {
            return new JumperConfig();
        }
    }
}
