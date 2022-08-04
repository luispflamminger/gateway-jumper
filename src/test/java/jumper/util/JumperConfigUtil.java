package jumper.util;

import jumper.model.config.JumperConfig;
import jumper.model.config.OauthSecurity;

import java.util.HashMap;

import static jumper.model.config.JumperConfig.toBase64;
import static jumper.util.Config.CONSUMER;
import static jumper.util.Config.SCOPES;

public class JumperConfigUtil{


    public static String getJcSecurity(){
        HashMap<String, String> m = new HashMap<>();
        m.put(CONSUMER, SCOPES);
        JumperConfig jc = new JumperConfig();
        jc.setOauthSecurity(new OauthSecurity(m));
        return toBase64(jc);
    }


}
