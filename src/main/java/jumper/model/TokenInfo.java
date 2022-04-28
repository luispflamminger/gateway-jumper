package jumper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class TokenInfo {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private int expiresIn;

    private Date expiration;

    @JsonProperty("refresh_expires_in")
    private int refreshExpiresIn;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("not-before-policy")
    private int notBeforePolicy;

    @JsonProperty("session_state")
    private String sessionState;

    private String scope;

    public void setExpiresIn(int expiresIn) {
        setExpiration(new Date(System.currentTimeMillis() + expiresIn * 1000));
    }

    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }
    
    public int getExpiresIn() {
    	//return Long.valueOf((Long.valueOf(expiresIn) - System.currentTimeMillis()) / 1000L).intValue();
        return expiration != null ? Long.valueOf((expiration.getTime() - System.currentTimeMillis()) / 1000L)
                .intValue() : 0;
    }
}
