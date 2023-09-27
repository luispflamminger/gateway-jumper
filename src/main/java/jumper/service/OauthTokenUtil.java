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
    private final BasicAuthUtil basicAuthUtil;

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

    public String generateEnhancedLastMileGatewayToken(JumperConfig jc, String operation, String issuer, String publisherId, String subscriberId, boolean legacy) {
        //nearly to pass additional claims as a map, so far scope + publisher

        String consumerTokenWithoutSignature = getTokenWithoutSignature(jc.getConsumerToken());

        Jwt<Header, Claims> gatewayTokenclaims = getAllClaimsFromToken(consumerTokenWithoutSignature);

        Date issuedAt = gatewayTokenclaims.getBody().getIssuedAt();
        Date expiration = gatewayTokenclaims.getBody().getExpiration();
        String clientId = gatewayTokenclaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class);
        String consumerOriginZone = gatewayTokenclaims.getBody().get("originZone", String.class);
        String consumerOriginStargate = gatewayTokenclaims.getBody().get("originStargate", String.class);
        String sub = gatewayTokenclaims.getBody().get(Constants.TOKEN_CLAIM_SUB, String.class);
        String aud = gatewayTokenclaims.getBody().get(Constants.TOKEN_CLAIM_AUD, String.class);

        HashMap<String, String> claims = new HashMap<>();
        claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
        claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
        claims.put(Constants.TOKEN_CLAIM_SUB, sub);
        claims.put(Constants.TOKEN_CLAIM_REQUEST_PATH, jc.getRequestPath());
        claims.put(Constants.TOKEN_CLAIM_OPERATION, operation);
        claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, clientId);
        claims.put(Constants.TOKEN_CLAIM_ORIGIN_ZONE, consumerOriginZone);
        claims.put(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, consumerOriginStargate);

        if (legacy) {
            String consumerTokenSignature = getSignature(jc.getConsumerToken());
            claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SIGNATURE, consumerTokenSignature);

        } else {
            claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT, jc.getEnvName());

            if (Objects.nonNull(jc.getSecurityScopes())) {
                claims.put(Constants.TOKEN_CLAIM_SCOPE, jc.getSecurityScopes());
            }

            if (Objects.nonNull(publisherId)) {
                claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID, publisherId);
            }

            if (Objects.nonNull(subscriberId)) {
                claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID, subscriberId);
                claims.put(Constants.TOKEN_CLAIM_AUD, subscriberId);
            }


        }

        if (StringUtils.isNotBlank(aud)) {
            claims.put(Constants.TOKEN_CLAIM_AUD, aud);
        }

        return generateToken(claims, issuer, expiration, issuedAt);
    }

    public String generateGatewayTokenForPublisher(String issuer) {
        HashMap<String, String> claims = new HashMap<>();
        claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
        claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
        claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, "gateway");

        return generateToken(claims,
                issuer,
                new Date(System.currentTimeMillis() + 300 * 1000),
                new Date(System.currentTimeMillis())
        );
    }


    private String generateToken(HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt) {
        KeyInfo keyInfo;

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

            MultiValueMap<String, String> requestParameter = new LinkedMultiValueMap<>();
            requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_ID, clientID);
            requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_CLIENT_SECRET, clientSecret);
            requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue());

            if (StringUtils.isNotBlank(scope)) {
                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_SCOPE, scope);
            }

            return getAccessTokenQuery(tokenEndpoint, tokenKey, requestParameter, null);

        });

    }

    public TokenInfo getAccessTokenWithOauthCredentialsObject(String tokenEndpoint, OauthCredentials oauthCredentials, String subscriberClientId) {

        final String tokenKey = tokenCache.generateTokenCacheKey(tokenEndpoint, oauthCredentials.getId(), subscriberClientId);

        // try to get valid token from tokenCache...
        return tokenCache.getToken(tokenKey).orElseGet(() -> { // ...otherwise retrieve a new one

            MultiValueMap<String, String> requestParameter = new LinkedMultiValueMap<>();
            String basicAuth = null;

            if (StringUtils.isNotBlank(oauthCredentials.getClientId())
                    && StringUtils.isNotBlank(oauthCredentials.getClientSecret())) {

                basicAuth = basicAuthUtil
                        .encodeBasicAuth(oauthCredentials.getClientId(), oauthCredentials.getClientSecret());
            }

            if (StringUtils.isNotBlank(oauthCredentials.getUsername())
                    && StringUtils.isNotBlank(oauthCredentials.getPassword())) {

                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_USERNAME, oauthCredentials.getUsername());
                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_PASSWORD, oauthCredentials.getPassword());
            }

            if (StringUtils.isNotBlank(oauthCredentials.getRefreshToken())) {
                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_REFRESH_TOKEN, oauthCredentials.getRefreshToken());
            }

            if (StringUtils.isNotEmpty(oauthCredentials.getScopes())) {
                requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_SCOPE, oauthCredentials.getScopes());
            }

            requestParameter.add(Constants.TOKEN_REQUEST_PARAMETER_GRANT_TYPE, oauthCredentials.getGrantType());

            return getAccessTokenQuery(tokenEndpoint, tokenKey, requestParameter, basicAuth);
        });
    }

    private TokenInfo getAccessTokenQuery(String tokenEndpoint, String tokenKey, MultiValueMap<String, String> formData, String basicAuthHeader) {

        Mono<TokenInfo> tokenInfoMono = webClient.post()
                .uri(tokenEndpoint)
                .headers(
                        httpHeaders -> {
                            httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                            if (basicAuthHeader != null) httpHeaders.setBasicAuth(basicAuthHeader);
                        }
                )
                .body(BodyInserters.fromFormData(formData))
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
