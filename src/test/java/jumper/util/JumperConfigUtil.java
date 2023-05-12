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

    public static String getJcOauth(){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(CONSUMER_EXTERNAL_CONFIGURED);
        oc.setClientSecret("secret");
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }

    public static String getJcOauthWithScope(){
        HashMap<String, OauthCredentials> oauth = new HashMap<>();
        OauthCredentials oc = new OauthCredentials();
        oc.setClientId(CONSUMER_EXTERNAL_CONFIGURED);
        oc.setClientSecret("secret");
        oc.setScopes(OAUTH_SCOPE_CONFIGURED);
        oauth.put(CONSUMER, oc);
        JumperConfig jc = new JumperConfig();
        jc.setOauth(oauth);
        return toBase64(jc);
    }


}
