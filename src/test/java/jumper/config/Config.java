package jumper.config;

public class Config {
    public final static String CONSUMER = "eni--local-team--local-app";
    public final static String CONSUMER_GATEWAY = "gateway";
    public final static String CONSUMER_EXTERNAL_CONFIGURED = "external_configured";
    public final static String CONSUMER_EXTERNAL_HEADER = "external_header";
    public final static String SCOPES = "scope1 scope2";
    public final static String OAUTH_SCOPE_CONFIGURED = "scope_configured";
    public final static String OAUTH_SCOPE_HEADER = "scope_header";
    public final static String ORIGIN_STARGATE = "https://zone.local.de";
    public final static String ORIGIN_STARGATE_REMOTE = "https://zone.remote.de";
    public final static String ORIGIN_ZONE = "localZone";
    public final static String ORIGIN_ZONE_REMOTE = "remoteZone";
    public final static String ENVIRONMENT = "localEnv";
    public final static String ENVIRONMENT_REMOTE = "remoteEnv";
    public final static String REALM = "default";
    public final static String BASE_PATH = "/eni/test/v1";
    public final static String LOCAL_ISSUER = "https://iris.local:1234/auth/realms/default";
    public final static String REMOTE_ISSUER = "https://iris.remote:1234/auth/realms/default";
    public final static String CALLBACK_SUFFIX = "/callback";
    public final static String PUBSUB_PUBLISHER = "testPublisher";
    public final static String PUBSUB_SUBSCRIBER = "testSubscriber";
    public final static String LISTENER_ISSUE = "issue";
    public final static String LISTENER_PROVIDER = "serviceOwner";
}
