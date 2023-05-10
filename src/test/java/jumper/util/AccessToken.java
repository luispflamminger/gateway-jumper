package jumper.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jumper.utilities.OauthTokenUtil;
import lombok.Builder;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Builder
public class AccessToken {

    private String clientId;
    private String env;
    private String originZone;
    private String originStargate;

    public String getConsumerAccessToken() {
        HashMap<String, String> claims = new HashMap<String, String>();
        claims.put( "typ", "Bearer");
        claims.put( "azp", clientId);
        claims.put( "sub", UUID.randomUUID().toString());
        claims.put( "originZone", originZone);
        claims.put( "originStargate", originStargate);
        claims.put( "clientId", clientId);

        return buildAccessToken(claims);
    }

    public String getGwMeshToken() {
        HashMap<String, String> claims = new HashMap<String, String>();
        claims.put( "typ", "Bearer");
        claims.put( "azp", "stargate");
        claims.put( "sub", UUID.randomUUID().toString());
        claims.put( "clientId", clientId);
        claims.put( "env", env);
        claims.put( "originZone", originZone);
        claims.put( "originStargate", originStargate);

        return buildAccessToken(claims);
    }


    private String buildAccessToken(Map<String, String> claims) {
        String issuer = "https://iris.remote:1234/auth/realms/default";


        Date issuedAt = new Date(System.currentTimeMillis());
        Date expiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
        PrivateKey privateKey = null;
        try {
            privateKey = OauthTokenUtil.loadPrivKey();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.getStackTrace();
        }

        String keyId = "123456";

        return Jwts.builder().setClaims(claims).setIssuer(issuer).setExpiration(expiration).setIssuedAt(issuedAt).signWith(privateKey, SignatureAlgorithm.RS256).setHeaderParam("kid", keyId).setHeaderParam("typ", "JWT").compact();

    }

}
