package jumper.util;

import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;

import java.util.HashMap;

import static jumper.model.config.JumperConfig.toBase64;
import static jumper.util.Config.CONSUMER;
import static jumper.util.Config.SCOPES;

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


}
