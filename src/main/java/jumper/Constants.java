package jumper;

public class Constants {

	public final static String HEADER_X_SPACEGATE_CLIENT_ID = "X-Spacegate-Client-ID";
    public final static String HEADER_X_SPACEGATE_CLIENT_SECRET = "X-Spacegate-Client-Secret";
    public final static String HEADER_X_SPACEGATE_SCOPE = "X-Spacegate-Scope";
    public final static String HEADER_JUMPER_CONFIG = "jumper_config";
    public final static String HEADER_ISSUER = "issuer";
    public final static String HEADER_TOKEN_ENDPOINT = "token_endpoint";
	public final static String HEADER_CLIENT_ID = "client_id";
	public final static String HEADER_CLIENT_SECRET = "client_secret";
	public final static String HEADER_CLIENT_SCOPES = "scopes";
	public final static String HEADER_CONSUMER_TOKEN = "consumer-token";
	public final static String HEADER_GATEWAY_TOKEN = "gateway_token";
	public final static String HEADER_LASTMILE_SECURITY_TOKEN = "X-Gateway-Token";
	public final static String HEADER_AUTHORIZATION = "Authorization";
	public final static String HEADER_REMOTE_API_URL = "remote_api_url";
	public final static String HEADER_ACCESS_TOKEN_FORWARDING = "access_token_forwarding";
	public final static String HEADER_REALM = "realm";
	public final static String HEADER_ENVIRONMENT = "environment";
	public final static String HEADER_DEBUG_RESPONSE_HEADER = "X-Tardis-Debug";
	public final static String HEADER_X_ORIGIN_STARGATE = "X-Origin-Stargate";
	public final static String HEADER_X_ORIGIN_ZONE = "X-Origin-Zone";
	public final static String HEADER_X_B3_TRACE_ID = "x-b3-traceid";
	public final static String HEADER_X_B3_SPAN_ID = "X-B3-SpanId";
	public final static String HEADER_X_B3_PARENT_SPAN_ID = "X-B3-ParentSpanId";
	public final static String HEADER_X_B3_SAMPLED= "X-B3-Sampled";
	public final static String HEADER_X_TARDIS_TRACE_ID = "x-tardis-traceid";
	public final static String HEADER_X_BUSINESS_CONTEXT = "x-business-context";
	public final static String HEADER_X_REQUEST_ID = "x-request-id";
	public final static String HEADER_X_CORRELATION_ID = "x-correlation-id";
	public final static String HEADER_X_PUBLISHER_ID = "x-pubsub-publisher-id";
	public final static String HEADER_X_SUBSCRIPTION_ID = "x-subscription-id";
	public final static String HEADER_B3 = "b3";

	public final static String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
	public final static String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";
	public final static String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
	
	public final static String HEADER_X_FORWARDED_PORT_PORT = "443";
	public final static String HEADER_X_FORWARDED_PROTO_HTTPS = "https";
	
	public final static String ISSUER_SUFFIX = "/protocol/openid-connect/token";
	public static final String HEADER_API_BASE_PATH = "api_base_path";

	public final static String LOCALHOST_ISSUER_SERVICE = "http://localhost:8081/api/v1";

	public final static String DEFAULT_REALM = "default";
	
	public final static String BEARER = "Bearer";

    public static final String SPACE = "space";
    public static final String HEADER_X_SPACEGATE_TOKEN = "X-Spacegate-Token";

	public static final String ENVIRONMENT_PLACEHOLDER = "ENVIRONMENT_PLACEHOLDER";
}
