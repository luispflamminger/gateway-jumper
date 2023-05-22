package jumper.util;

import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;

import java.util.HashMap;

import static jumper.model.config.JumperConfig.toBase64;
import static jumper.util.Config.*;

public class JumperConfigUtil{


    public static String getJcSecurity(){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setScopes(SCOPES);
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauth(String id){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED,id));
        oc.setClientSecret("secret");
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauthWithScope(String id){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
        oc.setClientSecret("secret");
        oc.setScopes(OAUTH_SCOPE_CONFIGURED);
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauthGrantType(String id){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
        oc.setClientSecret("secret");
        oc.setGrantType("client_credentials");
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauthGrantTypePassword(String id){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(addIdSuffix(CONSUMER_EXTERNAL_CONFIGURED, id));
        oc.setClientSecret("secret");
        oc.setUsername("username");
        oc.setPassword("geheim");
        oc.setGrantType("password");
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauthGrantTypePasswordOnly(String id){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setUsername(addIdSuffix("username", id));
        oc.setPassword("geheim");
        oc.setGrantType("password");
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String addIdSuffix (String from,  String id){
        return from + "_" + id;
    }

}
