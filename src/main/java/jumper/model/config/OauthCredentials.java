package jumper.model.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OauthCredentials
{
    private String clientId;
    private String clientSecret;
    private String scopes;
}
