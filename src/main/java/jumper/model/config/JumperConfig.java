package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jumper.Constants;
import jumper.service.HeaderUtil;
import jumper.service.OauthTokenUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
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
    String apiBasePath;
    String consumer;
    String consumerOriginStargate;
    String consumerOriginZone;
    String consumerToken;
    String externalTokenEndpoint;
    String internalTokenEndpoint;
    String clientId;
    String clientSecret;
    Boolean accessTokenForwarding;
    String realmName;
    String remoteApiUrl;
    String envName;
    String xSpacegateClientId;
    String xSpacegateClientSecret;
    String xSpacegateScope;

    //calculated routing stuff within requestFilter
    String requestPath;
    String routingPath;

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
        try {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(decodedJson, JumperConfig.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return new JumperConfig();
        }
    }

    @JsonIgnore
    public void fillWithLegacyHeaders(ServerHttpRequest request) {

        setScopes(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SCOPES));
        setApiBasePath(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_API_BASE_PATH));
        setExternalTokenEndpoint(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_TOKEN_ENDPOINT));
        setInternalTokenEndpoint(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ISSUER));
        setClientId(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_ID));
        setClientSecret(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SECRET));

        if (request.getHeaders().containsKey(Constants.HEADER_ACCESS_TOKEN_FORWARDING)) {
            setAccessTokenForwarding(Boolean.valueOf(
                    HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ACCESS_TOKEN_FORWARDING)
            ));
        }

        setConsumerToken(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
        setConsumer(OauthTokenUtil.getClaimFromToken(consumerToken,Constants.TOKEN_CLAIM_CLIENT_ID));
        setConsumerOriginStargate(OauthTokenUtil.getClaimFromToken(consumerToken,Constants.TOKEN_CLAIM_ORIGIN_STARGATE));
        setConsumerOriginZone(OauthTokenUtil.getClaimFromToken(consumerToken,Constants.TOKEN_CLAIM_ORIGIN_ZONE));

        setRealmName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REALM));
        if (StringUtils.isBlank(getRealmName())) {
            setRealmName(Constants.DEFAULT_REALM);
        }

        setRemoteApiUrl(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REMOTE_API_URL));
        setEnvName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ENVIRONMENT));
        setXSpacegateClientId(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID));
        setXSpacegateClientSecret(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET));
        setXSpacegateScope(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_SCOPE));
    }

    @JsonIgnore
    public static JumperConfig parseConfigFrom(ServerHttpRequest request) {

        JumperConfig jc;
        String jumperConfigBase64 = HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG);

        if (StringUtils.isNotBlank(jumperConfigBase64)) {
            jc = JumperConfig.fromBase64(jumperConfigBase64);

        } else {
            jc = new JumperConfig();
        }

        jc.fillWithLegacyHeaders(request); // TODO: remove as soon we have completely shifted to json_config

        return jc;
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
