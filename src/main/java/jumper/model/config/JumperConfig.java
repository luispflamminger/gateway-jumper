package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.Constants;
import jumper.utilities.OauthTokenUtil;
import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.util.Base64;
import java.util.HashMap;

@Data
public class JumperConfig {

    private HashMap<String, OauthCredentials> oauth;
    private HashMap<String, RouteListener> routeListener;
    private GatewayClient gatewayClient;
    private Security security;

    String token_endpoint;
    String tif_remote_issuer;
    String tif_clientID;
    String tif_clientSecret;
    String scopes;
    String consumerToken;
    String api_base_path;
    String access_token_forwarding;
    String realmName;
    String envName;

    String xB3TraceId;
    String xTardisTraceId;
    String xBusinessContext;
    String xRequestId;
    String xCorrelationId;

    String api_resource;
    String requestPath;
    String remote_api_url;

    String debugHeader;

    @JsonIgnore
    public static String toBase64(JumperConfig jc) {
        String jsonConfigBase64 = null;
        try
        {
            String decodedJson = new ObjectMapper().writeValueAsString( jc);
            jsonConfigBase64 = Base64.getEncoder().encodeToString( decodedJson.getBytes());
        }
        catch( JsonProcessingException e)
        {
            e.printStackTrace();
        }

        return jsonConfigBase64;
    }

    @JsonIgnore
    public static JumperConfig fromBase64(String jsonConfigBase64) {
        String decodedJson = new String(Base64.getDecoder().decode( jsonConfigBase64.getBytes()));
        JumperConfig jc = null;
        try
        {
            jc = new ObjectMapper().readValue( decodedJson, JumperConfig.class);
        }
        catch( JsonProcessingException e)
        {
            e.printStackTrace();
        }

        return jc;
    }

    @JsonIgnore
    public void fillWithLegacyHeaders( ServerHttpRequest request) {
        token_endpoint = request.getHeaders().getFirst( Constants.HEADER_TOKEN_ENDPOINT);
        tif_remote_issuer = request.getHeaders().getFirst( Constants.HEADER_ISSUER);
        tif_clientID = request.getHeaders().getFirst( Constants.HEADER_CLIENT_ID);
        tif_clientSecret = request.getHeaders().getFirst( Constants.HEADER_CLIENT_SECRET);
        scopes = request.getHeaders().getFirst( Constants.HEADER_CLIENT_SCOPES);
        consumerToken = request.getHeaders().getFirst( Constants.HEADER_AUTHORIZATION);
        api_base_path = request.getHeaders().getFirst( Constants.HEADER_API_BASE_PATH);
        access_token_forwarding = request.getHeaders().getFirst( Constants.HEADER_ACCESS_TOKEN_FORWARDING);
        realmName = request.getHeaders().getFirst( Constants.HEADER_REALM);
        envName = request.getHeaders().getFirst( Constants.HEADER_ENVIRONMENT);

        xB3TraceId = request.getHeaders().getFirst( Constants.HEADER_X_B3_TRACE_ID);
        xTardisTraceId = request.getHeaders().getFirst( Constants.HEADER_X_TARDIS_TRACE_ID);
        xBusinessContext = request.getHeaders().getFirst( Constants.HEADER_X_BUSINESS_CONTEXT);
        xRequestId = request.getHeaders().getFirst( Constants.HEADER_X_BUSINESS_CONTEXT);
        xCorrelationId = request.getHeaders().getFirst( Constants.HEADER_X_CORRELATION_ID);

        api_resource = request.getPath().value();
        requestPath = api_base_path + api_resource;
        remote_api_url = request.getHeaders().getFirst( Constants.HEADER_REMOTE_API_URL);

        debugHeader = request.getHeaders().getFirst( Constants.HEADER_DEBUG_RESPONSE_HEADER);
    }

    @JsonIgnore
    public static JumperConfig parseConfigFrom(ServerHttpRequest req) {
        // jumper config
        JumperConfig jc = null;
        String jumper_config_Base64 = req.getHeaders().getFirst(Constants.HEADER_JUMPER_CONFIG);
        if (jumper_config_Base64 != null && !jumper_config_Base64.isEmpty())
        {
            jc = JumperConfig.fromBase64( jumper_config_Base64);
            jc.fillWithLegacyHeaders( req); // TODO: remove as soon we have completely shifted to json_config
        }
        else
        {
            jc = new JumperConfig();
            jc.fillWithLegacyHeaders( req);
        } // TODO: remove as soon we have completely shifted to json_config

        return jc;

    }

    @JsonIgnore
    public static JumperConfig parseConfigFrom(ServerWebExchange exchange){
        JumperConfig jc = null;
        String jumper_config_Base64 = exchange.getAttribute(Constants.HEADER_JUMPER_CONFIG);
        if (jumper_config_Base64 != null && !jumper_config_Base64.isEmpty()) {
            jc = JumperConfig.fromBase64(jumper_config_Base64);
        }
        else{
            jc = new JumperConfig();
        }
        return jc;
    }

    @JsonIgnore
    public String getConsumer() {
        return OauthTokenUtil.getConsumerFromToken( consumerToken);
    }
}
