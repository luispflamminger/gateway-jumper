package jumper.utilities;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.InvalidKeyException;
import io.jsonwebtoken.security.SignatureException;
import io.netty.channel.ConnectTimeoutException;
import jumper.JumperCache;
import jumper.model.TokenInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
@Service
public class OauthTokenUtil {
	WebClient webClient = WebClient.create();

	@Autowired
	JumperCache tokenCache;

	private static String keyId = "74f16025-ff3d-453b-a917-eca975423dea";

	public static String getTokenWithoutSignature( String consumerToken) {

		String[] token = consumerToken.split( " ");
		String[] splitToken = token[1].split( "\\.");
		String consumerTokenWithoutSignature = splitToken[0]+"."+splitToken[1]+".";

		return consumerTokenWithoutSignature;
	}

	public static String getConsumerFromToken( String consumerToken) {
		return getClaimFromToken(consumerToken, "clientId");
	}

	public static String getClaimFromToken(String consumerToken, String claimName){
		String consumerTokenWithoutSignature = getTokenWithoutSignature( consumerToken);
		Jwt<Header, Claims> consumerTokenclaims = getAllClaimsFromConsumerToken( consumerTokenWithoutSignature);
		String claimValue = consumerTokenclaims.getBody().get( claimName, String.class);
		return claimValue;
	}

	public static Jwt<Header, Claims> getAllClaimsFromConsumerToken( String consumerToken) {

		try
		{
			return Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build().parseClaimsJwt( consumerToken);
		}
		catch( SignatureException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch( ExpiredJwtException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch( UnsupportedJwtException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch( MalformedJwtException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch( IllegalArgumentException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static String generateExtGatewayToken( String envName, String consumerToken, String operation, String requestPath, String issuer) {

		String[] token = consumerToken.split( " ");
		String[] splitToken = token[1].split( "\\.");
		String consumerTokenWithoutSignature = splitToken[0]+"."+splitToken[1]+".";

		Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromConsumerToken( consumerTokenWithoutSignature);

		Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
//	        Date now = new Date();
//	        Date expiration = new Date(now.getTime() + 3600_000L * 24 * 30);;
		Date expiration = gatewayTokenclaims.getBody().getExpiration();
		String clientId = gatewayTokenclaims.getBody().get( "clientId", String.class);
		String consumerOriginZone = gatewayTokenclaims.getBody().get( "originZone", String.class);
		String consumerOriginStargate = gatewayTokenclaims.getBody().get( "originStargate", String.class);
		String sub = gatewayTokenclaims.getBody().get( "sub", String.class);

		String privateKey = null;
		// String privateKey =
		// "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDInVuimjkCL+rjxn36ddYNQxZvwXy4GybDUALKB0iTFGCKgxDGBtH9odLGT6GIcWLnU0F1JbsdVFvdBgOhs83Nrd2Aqm7hIPpdsIAHt2qdgyUAMfwYws83ozB7ephPNEUFKCcKTqh1RfcnpLKodv2+kkoboEMQUxsEBlPB/EAY4Kcpwbe/jYyTv/w7Y9QnAcQRvnXKy0YwcOcWigTljI1L88tukr7ORyuhhw1gOBHw+eS6LYK2thAYt84a5jr0gHUG4MKkMnx6n0p+2CwpoIzD9Sg5qzqeoqKT9R5qg7/uo2t66sO2awcotZCEJWEyNVZpRtYFOxLASuwtanne3WM3AgMBAAECggEBAIfpk9tlgJnqvMf0AgVdL9dsTBcKjuRsAKbx3fHhXVnHxGASy5pdpIagy5tu95DowIjX7tDe7xW/wTzMhklW92cRE6/Hx7beEMDIgvS3XpO39alcM97SnHClLoN5WxbN4rTLrydcguRwsjE5c5COjPo/QdXVjZnRs8vWPYh+zS4MUF95n/pLFUe0ZDrboozjYDL88m7OXDIGcUf8gOfJdd3i7rC15iiLyKgrcCmL0e69qRFMpTM6v2EzWnZrKeyLQKByDlJZcW1irvLEL4hZE8inyLKfzB73KNo07YdJbofB418w3lAFYqO0fNWorF+tPldTXLFO6p4vip5sWFOnIYECgYEA/DzauAGgUt/gLt6rSyPslrBC9GU4cYdTMT6lBcJWoj95ZIqnzTjtUriCZrNor2+LYES8zH3T3nzaj4axWj50IU4K0ZMdmO1Izzur3FAsJJd2Za88Ne6m3cUs0AQVFeGV70JEE6+kKalOP8SG9AT9oDsnCb/JQBOkDwxNaO8/jkUCgYEAy5tizf7O+BhfmFJMD75SFJ7YvcEVTeC6nMKBOH5jaXv12uwxqVvd2nSqGoPfjhR78PQR1sm4S9Tz3wqgQeaLAXUzCAMbUyNQ963kEK03W7WzJsJKH+izHwXHD/eY4dtTiGRyTX71zmh74nLVTgobN5Vv2YdWii5MxlrSw6DtsUsCgYBxIJXz6v7NzIzOWJ24sJ7+ooUU+YTMHiZosrDumU+jqxY1yp4hw8Nk003g49wyurNm9M08Zb6tTY/0yTMnx1TsTwU5I2Ml4F5EW33j7K0vqCK4zlQR2DxMwI8tqHcQfkFxsmW38pGNAdsPbIQeU1KxF3aVv8dyDp0JBrp9MrhthQKBgEyGgoRaGQA2aPefNudT6RXG/j+TqqYyqPDySg8pscOby7QUwjWdSa0p3CVLG2MTX+IYWfwYpSQbTe2u2LzsIaLSofOI92QwCeaNfQKnl/7oNAWFUMbddzVZvo/Jx7Rb8vF4j12BMnH541YhQvqp4cDqcbeYnnYhIMoMqNrOSYgxAoGBAMO9zkUBWyD+L8E7huc4+8MxuLgjvrW562vGh5J/d3GZaNRz/Ra0ygQjzcyASLFffYP2T8HorHrToUwn1ha4wsjqYYveOjrQ4QgbW7c6Gi9zYoErwsFDt9BBfg/tH7JAciUPpLvKgAQDY9vSuw+39IcjTqkvB4aAEx8zt320Bkhe";
		PrivateKey loadKey = null;
		try
		{
			loadKey = loadPrivKey( privateKey);
		}
		catch( NoSuchAlgorithmException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( InvalidKeySpecException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( IOException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( URISyntaxException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		HashMap<String, String> claims = new HashMap<String, String>();
		claims.put( "typ", "Bearer");
		claims.put( "azp", "stargate");
		claims.put( "sub", sub);
		claims.put( "requestPath", requestPath);
		claims.put( "operation", operation);
		claims.put( "clientId", clientId);
		claims.put( "env", envName);
		claims.put( "originZone", consumerOriginZone);
		claims.put( "originStargate", consumerOriginStargate);

		return Jwts.builder().setClaims( claims).setIssuer( issuer).setExpiration( expiration).setIssuedAt( issuedAt).signWith( loadKey, SignatureAlgorithm.RS256).setHeaderParam( "kid", keyId).setHeaderParam( "typ", "JWT").compact();
	}

	public static String generateGatewayToken( String envName, String consumerToken, String operation, String requestPath, String issuer) {

		String[] token = consumerToken.split( " ");
		String[] splitToken = token[1].split( "\\.");
		String consumerTokenWithoutSignature = splitToken[0]+"."+splitToken[1]+".";
		String signature = splitToken[2];

		Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromConsumerToken( consumerTokenWithoutSignature);

		Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
		Date expiration = gatewayTokenclaims.getBody().getExpiration();
		String clientId = gatewayTokenclaims.getBody().get( "clientId", String.class);
		String consumerOriginZone = gatewayTokenclaims.getBody().get( "originZone", String.class);
		String consumerOriginStargate = gatewayTokenclaims.getBody().get( "originStargate", String.class);
		String sub = gatewayTokenclaims.getBody().get( "sub", String.class);

		String privateKey = null;
		PrivateKey loadKey = null;
		try
		{
			loadKey = loadPrivKey( privateKey);
		}
		catch( NoSuchAlgorithmException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( InvalidKeySpecException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( IOException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch( URISyntaxException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		HashMap<String, String> claims = new HashMap<String, String>();
		claims.put( "typ", "Bearer");
		claims.put( "azp", "stargate");
		claims.put( "sub", sub);
		claims.put( "requestPath", requestPath);
		claims.put( "operation", operation);
//	      claims.put("env", envName);
		claims.put( "accessTokenSignature", signature);
		claims.put( "originZone", consumerOriginZone);
		claims.put( "originStargate", consumerOriginStargate);

		return Jwts.builder().setClaims( claims).setIssuer( issuer).setExpiration( expiration).setIssuedAt( issuedAt).signWith( loadKey, SignatureAlgorithm.RS256).setHeaderParam( "kid", keyId).setHeaderParam( "typ", "JWT").compact();
	}

	public static PrivateKey loadPrivKey( String key) throws IOException, URISyntaxException, NoSuchAlgorithmException, InvalidKeySpecException {

		String privateKeyContent;
		if( key == null)
		{
			File projectDir = new File( System.getProperty( "user.dir")+"/keypair/app.pem");

			privateKeyContent = new String( Files.readAllBytes( Path.of( projectDir.toURI())));
			privateKeyContent = privateKeyContent.replaceAll( "(\\r|\\n)", "").replace( "-----BEGIN PRIVATE KEY-----", "").replace( "-----END PRIVATE KEY-----", "");
		}
		else
		{
			privateKeyContent = key;
		}

		KeyFactory kf = KeyFactory.getInstance( "RSA");

		PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec( Base64.getDecoder().decode( privateKeyContent));
		PrivateKey privKey = kf.generatePrivate( keySpecPKCS8);

		return privKey;
	}

	public TokenInfo getAccessToken(String token_endpoint2, String tif_clientID2, String tif_clientSecret2) {
		return getAccessToken(token_endpoint2, tif_clientID2, tif_clientSecret2, false);
	}

	public TokenInfo getAccessToken(String token_endpoint2, String tif_clientID2, String tif_clientSecret2, boolean autoevent) {

		// (cache) try to grab a valid gateway mesh token from cache
		if (log.isDebugEnabled()) {
			tokenCache.printCache();
		}
		final String tokenKey = token_endpoint2 + tif_clientID2;

		TokenInfo gwAccessToken = tokenCache.getToken( tokenKey);


		if (gwAccessToken == null) {

			MultiValueMap<String, String> cc = new LinkedMultiValueMap<>();
			cc.add("client_id", tif_clientID2);
			cc.add("client_secret", tif_clientSecret2);
			cc.add("grant_type", AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

			/*
			Mono blockingWrapper = Mono.fromCallable(() -> {
				return /* make a remote synchronous call /
					});
					blockingWrapper = blockingWrapper.subscribeOn(Schedulers.boundedElastic());
			 */


			//for autovent we do not throw exceptions
			if (autoevent) {
				try {
					gwAccessToken = getTokenInfoMono(token_endpoint2, cc).toFuture().get(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					log.error("InterruptedException occured: {}", e.getMessage());
				} catch (ExecutionException e) {
					log.error("ExecutionException occured: {}", e.getMessage());
				} catch (TimeoutException e) {
					log.error("TimeoutException occured: {}", e.getMessage());
				} catch (RuntimeException e) {
					log.error("RuntimeException occured: {}", e.getMessage());
				}

				if (gwAccessToken == null) {
					log.error("failed to get access token for {}", tif_clientID2);
				} else {
					// cache the gateway mesh token
					tokenCache.saveToken(tokenKey, gwAccessToken);
				}

			} else {

				// get GW mesh token from remote IDP
				gwAccessToken = webClient.post()
						.uri(token_endpoint2)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
						.body(BodyInserters.fromFormData(cc))
						.retrieve()
						.bodyToMono(TokenInfo.class)
						.retryWhen(Retry.max(3)
						.filter(throwable -> throwable instanceof ConnectTimeoutException))
						.onErrorMap(e -> new RuntimeException("message",e))
						.block();
/*
				try {
					gwAccessToken = getTokenInfoMono(token_endpoint2, cc).toFuture().thenApplyAsync(tokenInfo -> tokenInfo).get(30, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					throw new RuntimeException("InterruptedException", e);
				} catch (ExecutionException e) {
					throw new RuntimeException("ExecutionException", e);
				} catch (TimeoutException e) {
					throw new RuntimeException("TimeoutException", e);
				}
*/

				if (gwAccessToken == null) {
					throw new RuntimeException("could not get access token");
				}
				// cache the gateway mesh token
				tokenCache.saveToken(tokenKey, gwAccessToken);
			}

		}


		return gwAccessToken;

	}
	public Mono<TokenInfo> getTokenInfoMono(final String _uri, MultiValueMap<String, String> cc) {
		final Mono<TokenInfo> responseMono = webClient.post()
				.uri(_uri)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
				.body(BodyInserters.fromFormData(cc))
				.retrieve()
				.bodyToMono(TokenInfo.class)
				.publishOn(Schedulers.single())
				//.doOnNext(tokenInfo -> log.info("doOnNext {}", tokenInfo.getAccessToken()))
				.onErrorMap(e -> new RuntimeException("message", e));
		;
		return responseMono.flatMap(response -> {
			final String accessToken = response.getAccessToken();
			// Use `field` to do something that would produce a log message
			log.info("Got token: {}", accessToken);
			return Mono.just(response);
		});
	}

}
