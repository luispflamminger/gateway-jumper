package jumper.utilities;

import io.jsonwebtoken.*;
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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

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

@Slf4j
@Service
public class OauthTokenUtil {
	//WebClient webClient = WebClient.create();

	@Autowired
	private WebClient webClient;
	
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
		Jwt<Header, Claims> consumerTokenclaims = getAllClaimsFromToken( consumerTokenWithoutSignature);
		String claimValue = consumerTokenclaims.getBody().get( claimName, String.class);
		return claimValue;
	}

	public static Jwt<Header, Claims> getAllClaimsFromToken( String consumerToken) {

		try
		{
			return Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build().parseClaimsJwt( consumerToken);
		}
		catch( SignatureException e)
		{
			log.error("SignatureException", e);
		}
		catch( ExpiredJwtException e)
		{
			log.error("ExpiredJwtException", e);
		}
		catch( UnsupportedJwtException e)
		{
			log.error("UnsupportedJwtException", e);
		}
		catch( MalformedJwtException e)
		{
			log.error("MalformedJwtException", e);
		}
		catch( IllegalArgumentException e)
		{
			log.error("IllegalArgumentException", e);
		}
		throw new IllegalStateException("Was not able to parse consumer token");
		//return null;
	}

	public static String generateExtGatewayToken(String envName, String consumerToken, String operation, String requestPath, String issuer, String scope, String publisher) {
		//nearly to pass additional claims as a map, so far scope + publisher

		String[] token = consumerToken.split( " ");
		String[] splitToken = token[1].split( "\\.");
		String consumerTokenWithoutSignature = splitToken[0]+"."+splitToken[1]+".";

		Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromToken( consumerTokenWithoutSignature);

		Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
//	        Date now = new Date();
//	        Date expiration = new Date(now.getTime() + 3600_000L * 24 * 30);;
		Date expiration = gatewayTokenclaims.getBody().getExpiration();
		String clientId = gatewayTokenclaims.getBody().get( "clientId", String.class);
		String consumerOriginZone = gatewayTokenclaims.getBody().get( "originZone", String.class);
		String consumerOriginStargate = gatewayTokenclaims.getBody().get( "originStargate", String.class);
		String sub = gatewayTokenclaims.getBody().get( "sub", String.class);
		String aud = gatewayTokenclaims.getBody().get( "aud", String.class);

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
		if (scope != null) claims.put( "scope", scope);
		if (publisher != null) claims.put ("publisherId", publisher);
		if(!StringUtils.isEmpty(aud)) {
			claims.put("aud", aud);
		}

		//return Jwts.builder().setClaims( claims).setIssuer( issuer).setExpiration( expiration).setIssuedAt( issuedAt).signWith( loadKey, SignatureAlgorithm.RS256).setHeaderParam( "kid", keyId).setHeaderParam( "typ", "JWT").compact();
		return generateToken(claims, issuer, expiration, issuedAt);
	}

	public static String generateGatewayToken( String envName, String consumerToken, String operation, String requestPath, String issuer) {


		String[] token = consumerToken.split( " ");
		String[] splitToken = token[1].split( "\\.");
		String consumerTokenWithoutSignature = splitToken[0]+"."+splitToken[1]+".";
		String signature = splitToken[2];

		Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromToken( consumerTokenWithoutSignature);

		Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
		Date expiration = gatewayTokenclaims.getBody().getExpiration();
		String clientId = gatewayTokenclaims.getBody().get( "clientId", String.class);
		String consumerOriginZone = gatewayTokenclaims.getBody().get( "originZone", String.class);
		String consumerOriginStargate = gatewayTokenclaims.getBody().get( "originStargate", String.class);
		String sub = gatewayTokenclaims.getBody().get( "sub", String.class);
		String aud = gatewayTokenclaims.getBody().get( "aud", String.class);

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
		claims.put( "clientId", clientId);
		if(!StringUtils.isEmpty(aud)) {
			claims.put("aud", aud);
		}

		//return Jwts.builder().setClaims( claims).setIssuer( issuer).setExpiration( expiration).setIssuedAt( issuedAt).signWith( loadKey, SignatureAlgorithm.RS256).setHeaderParam( "kid", keyId).setHeaderParam( "typ", "JWT").compact();
		return generateToken(claims, issuer, expiration, issuedAt);
	}

	public static String generateGatewayTokenForPublisher(String issuer){
		HashMap<String, String> claims = new HashMap<String, String>();
		claims.put( "typ", "Bearer");
		claims.put( "azp", "stargate");
		claims.put( "clientId", "gateway");

		return generateToken(claims,
				issuer,
				new Date(System.currentTimeMillis() + 300 * 1000),
				new Date(System.currentTimeMillis())
				);
	}


	private static String generateToken(HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt){
		String privateKey = null;
		PrivateKey loadKey = null;
		try
		{
			log.info("GatewayToken or OneToken: Loading privateKey");
			loadKey = loadPrivKey( privateKey);
		}
		catch( NoSuchAlgorithmException e1)
		{
			log.error("NoSuchAlgorithmException", e1);
		}
		catch( InvalidKeySpecException e1)
		{
			log.error("InvalidKeySpecException", e1);
		}
		catch( IOException e1)
		{
			log.error("IOException", e1);
		}
		catch( URISyntaxException e1)
		{
			log.error("URISyntaxException", e1);
		}

		log.info("GatewayToken or OneToken: Generating with all claims");
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
	
	public TokenInfo getAccessToken(String token_endpoint2, String tif_clientID2, String tif_clientSecret2, String scope, String subscriberClientId) {
		return getAccessToken(token_endpoint2, tif_clientID2, tif_clientSecret2, false, scope, subscriberClientId);
	}

	public TokenInfo getAccessToken(String token_endpoint2, String tif_clientID2, String tif_clientSecret2) {
		return getAccessToken(token_endpoint2, tif_clientID2, tif_clientSecret2, false, null, "");
	}

	public TokenInfo getAccessToken(String token_endpoint2, String tif_clientID2, String tif_clientSecret2, boolean autoevent, String scope, String subscriberClientId) {

		// (cache) try to grab a valid gateway mesh token from cache
		if (log.isDebugEnabled()) {
			tokenCache.printCache();
		}
		final String tokenKey = token_endpoint2 + tif_clientID2 + subscriberClientId;

		TokenInfo gwAccessToken = tokenCache.getToken( tokenKey);


		if (gwAccessToken == null) {

			MultiValueMap<String, String> cc = new LinkedMultiValueMap<>();
			cc.add("client_id", tif_clientID2);
			cc.add("client_secret", tif_clientSecret2);
			cc.add("grant_type", AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());
			if(scope != null && !scope.isEmpty()) {
				cc.add("scope", scope);
			}

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
