package jumper.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.netty.channel.ConnectTimeoutException;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.KeyInfo;
import jumper.model.config.OauthCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OauthTokenUtil {

    private final WebClient webClient;
    private final TokenCacheService tokenCache;
    private final BasicAuthUtilService basicAuthUtilService;

    private static String securityPath;
    private static String securityFile;


    @Value("${jumper.security.dir:keypair}")
    private void setSecurityPath(String name){
        securityPath = name;
    }

    @Value("${jumper.security.file:key.json}")
    private void setSecurityFile(String name){
        securityFile = name;
    }


    public static String getTokenWithoutSignature(String consumerToken) {

		if (Objects.isNull(consumerToken)) {
			throw new IllegalStateException("Consumer token not provided, but expected");
		}

        String[] token = consumerToken.split(" ");
        String[] splitToken = token[1].split("\\.");

        return splitToken[0] + "." + splitToken[1] + ".";
    }

    private String getSignature(String consumerToken) {

        if (Objects.isNull(consumerToken)) {
            throw new IllegalStateException("Consumer token not provided, but expected");
        }

        String[] token = consumerToken.split(" ");
        String[] splitToken = token[1].split("\\.");

        return splitToken[2];
    }

    public static String getClaimFromToken(String consumerToken, String claimName) {
        String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);
        return getAllClaimsFromToken(consumerTokenWithoutSignature).getBody().get(claimName, String.class);
    }

    public static Jwt<Header, Claims> getAllClaimsFromToken(String consumerToken) {

        try {
            return Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build().parseClaimsJwt(consumerToken);
        } catch (SignatureException e) {
            log.error("SignatureException", e);
        } catch (ExpiredJwtException e) {
            log.error("ExpiredJwtException", e);
        } catch (UnsupportedJwtException e) {
            log.error("UnsupportedJwtException", e);
        } catch (MalformedJwtException e) {
            log.error("MalformedJwtException", e);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException", e);
        }
        throw new IllegalStateException("Was not able to parse consumer token");
    }

    public String generateEnhancedLastMileGatewayToken(String envName, String consumerToken, String operation, String requestPath, String issuer, String scope, String publisherId, String subscriberId) {
        //nearly to pass additional claims as a map, so far scope + publisher

        String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);

        Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromToken(consumerTokenWithoutSignature);

        Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
        Date expiration = gatewayTokenclaims.getBody().getExpiration();
        String clientId = gatewayTokenclaims.getBody().get("clientId", String.class);
        String consumerOriginZone = gatewayTokenclaims.getBody().get("originZone", String.class);
        String consumerOriginStargate = gatewayTokenclaims.getBody().get("originStargate", String.class);
        String sub = gatewayTokenclaims.getBody().get("sub", String.class);
        String aud = gatewayTokenclaims.getBody().get("aud", String.class);

        HashMap<String, String> claims = new HashMap<String, String>();
        claims.put("typ", "Bearer");
        claims.put("azp", "stargate");
        claims.put("sub", sub);
        claims.put("requestPath", requestPath);
        claims.put("operation", operation);
        claims.put("clientId", clientId);
        claims.put("originZone", consumerOriginZone);
        claims.put("originStargate", consumerOriginStargate);

        claims.put("env", envName);

        if (Objects.nonNull(scope)) {
            claims.put("scope", scope);
        }

        if (Objects.nonNull(publisherId)) {
            claims.put("publisherId", publisherId);
        }

        if (Objects.nonNull(subscriberId)) {
            claims.put("subscriberId", subscriberId);
            claims.put("aud", subscriberId);
        }

        if (StringUtils.isNotBlank(aud)) {
            claims.put("aud", aud);
        }

        return generateToken(claims, issuer, expiration, issuedAt);
    }

    public String generateLegacyLastMileGatewayToken(String envName, String consumerToken, String operation, String requestPath, String issuer) {


        String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);

        Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromToken(consumerTokenWithoutSignature);

        Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
        Date expiration = gatewayTokenclaims.getBody().getExpiration();
        String clientId = gatewayTokenclaims.getBody().get("clientId", String.class);
        String consumerOriginZone = gatewayTokenclaims.getBody().get("originZone", String.class);
        String consumerOriginStargate = gatewayTokenclaims.getBody().get("originStargate", String.class);
        String sub = gatewayTokenclaims.getBody().get("sub", String.class);
        String aud = gatewayTokenclaims.getBody().get("aud", String.class);

        HashMap<String, String> claims = new HashMap<String, String>();
        claims.put("typ", "Bearer");
        claims.put("azp", "stargate");
        claims.put("sub", sub);
        claims.put("requestPath", requestPath);
        claims.put("operation", operation);
        claims.put("clientId", clientId);
        claims.put("originZone", consumerOriginZone);
        claims.put("originStargate", consumerOriginStargate);

        if (StringUtils.isNotBlank(aud)) {
            claims.put("aud", aud);
        }

        String consumerTokenSignature = getSignature(consumerToken);
        claims.put("accessTokenSignature", consumerTokenSignature);

        return generateToken(claims, issuer, expiration, issuedAt);
    }

    public String generateGatewayTokenForPublisher(String issuer) {
        HashMap<String, String> claims = new HashMap<>();
        claims.put("typ", "Bearer");
        claims.put("azp", "stargate");
        claims.put("clientId", "gateway");

        return generateToken(claims,
                issuer,
                new Date(System.currentTimeMillis() + 300 * 1000),
                new Date(System.currentTimeMillis())
        );
    }


    private String generateToken(HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt) {
        KeyInfo keyInfo = null;

        try {
            log.debug("GatewayToken or OneToken: Loading keyInfo");
            keyInfo = loadKeyinfo();

        } catch (IOException e1) {
            log.error("IOException", e1);
            throw new RuntimeException("Error while generating LMS token, key info missing");
        }

        return Jwts.builder()
                .setClaims(claims)
                .setIssuer(issuer)
                .setExpiration(expiration)
                .setIssuedAt(issuedAt)
                .signWith(keyInfo.getPk(), SignatureAlgorithm.RS256)
                .setHeaderParam("kid", keyInfo.getKid())
                .setHeaderParam("typ", "JWT")
                .compact();
    }

    public static KeyInfo loadKeyinfo() throws IOException {
        Path kidFile = Path.of(System.getProperty("user.dir")  + File.separator + securityPath + File.separator + securityFile);
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue( Files.readString(kidFile), KeyInfo.class);
    }


    public TokenInfo getInternalMeshAccessToken(JumperConfig jc) {
        return getAccessTokenWithClientCredentials(jc.getInternalTokenEndpoint() + Constants.ISSUER_SUFFIX, jc.getClientId(), jc.getClientSecret(), null, "");
    }

    public TokenInfo getAccessTokenWithClientCredentials(String tokenEndpoint, String clientID, String clientSecret, String scope, String subscriberClientId) {

        final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, clientID, subscriberClientId);

        // try to get valid token from tokenCache...
        return tokenCache.getToken(tokenKey).orElseGet(() -> {  // ...otherwise retrieve a new one

            MultiValueMap<String, String> claims = new LinkedMultiValueMap<>();
            claims.add("client_id", clientID);
            claims.add("client_secret", clientSecret);
            claims.add("grant_type", AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

            if (StringUtils.isNotBlank(scope)) {
                claims.add("scope", scope);
            }

            return getAccessTokenQuery(tokenEndpoint, tokenKey, claims, null);

        });

    }

    public TokenInfo getAccessTokenWithOauthCredentialsObject(String tokenEndpoint, OauthCredentials oauthCredentials, String subscriberClientId) {

        final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, oauthCredentials.getId(), subscriberClientId);

        // try to get valid token from tokenCache...
        return tokenCache.getToken(tokenKey).orElseGet(() -> { // ...otherwise retrieve a new one

            MultiValueMap<String, String> cc = new LinkedMultiValueMap<>();
            String basicAuth = null;

            if (StringUtils.isNotBlank(oauthCredentials.getClientId())
                    && StringUtils.isNotBlank(oauthCredentials.getClientSecret())) {

                basicAuth = basicAuthUtilService
                        .encodeBasicAuth(oauthCredentials.getClientId(), oauthCredentials.getClientSecret());
            }

            if (StringUtils.isNotBlank(oauthCredentials.getUsername())
                    && StringUtils.isNotBlank(oauthCredentials.getPassword())) {

                cc.add("username", oauthCredentials.getUsername());
                cc.add("password", oauthCredentials.getPassword());
            }

            if (StringUtils.isNotBlank(oauthCredentials.getRefreshToken())) {
                cc.add("refresh_token", oauthCredentials.getRefreshToken());
            }

            if (StringUtils.isNotEmpty(oauthCredentials.getScopes())) {
                cc.add("scope", oauthCredentials.getScopes());
            }

            cc.add("grant_type", oauthCredentials.getGrantType());

            return getAccessTokenQuery(tokenEndpoint, tokenKey, cc, basicAuth);
        });
    }

    private TokenInfo getAccessTokenQuery(String tokenEndpoint, String tokenKey, MultiValueMap<String, String> claims, String basicAuthHeader) {

        Mono<TokenInfo> tokenInfoMono = webClient.post()
                .uri(tokenEndpoint)
                .headers(
                        httpHeaders -> {
                            httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                            if (basicAuthHeader != null) httpHeaders.setBasicAuth(basicAuthHeader);
                        }
                )
                .body(BodyInserters.fromFormData(claims))
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError,
                        response -> {
                            logClientErrorResponse(response, tokenKey);
                            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to retrieve token from " + tokenEndpoint));
                        })
                .bodyToMono(TokenInfo.class)
                .retryWhen(Retry.max(2)
                        .filter(ConnectTimeoutException.class::isInstance)
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                    throw new ServerErrorException("Failed to connect to " + tokenEndpoint, (Throwable) null);
                                }
                        )
                );

        CompletableFuture<TokenInfo> tokenInfoCompletableFuture = tokenInfoMono.toFuture().orTimeout(15, TimeUnit.SECONDS);
        TokenInfo accessToken = tokenInfoCompletableFuture.join();
        tokenCache.saveToken(tokenKey, accessToken);

        return accessToken;
    }

    private void logClientErrorResponse(ClientResponse response, String tokenKey) {
        response.bodyToMono(String.class)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(body -> log.warn("Client error occurred while getting token for tokenKey {}: {}", tokenKey, body));
    }

}
