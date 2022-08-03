package jumper.mocks;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import jumper.Constants;
import jumper.utilities.OauthTokenUtil;
import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Optional;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class TestExpectationCallback implements ExpectationCallback {
    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        if (httpRequest.getPath().getValue().endsWith("/callback")) {
            List<Header> httpRequestHeaders = httpRequest.getHeaders();
            List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
            Optional<Parameter> statusCode = queryStringParameters.stream().filter(param -> param.getName().equals("statusCode")).findFirst();
            Optional<Header> authorizationHeader = httpRequest.getHeaders().stream().filter(header -> header.getName().equals(HttpHeaders.AUTHORIZATION)).findFirst();
            if(authorizationHeader.isPresent()) {
                String jwtTokenSig = authorizationHeader.get().getValues().get(0).getValue();
                String jwtToken = OauthTokenUtil.getTokenWithoutSignature(jwtTokenSig);
                Jwt<io.jsonwebtoken.Header, Claims> allClaimsFromConsumerToken = OauthTokenUtil.getAllClaimsFromConsumerToken(jwtToken);
                String clientId = allClaimsFromConsumerToken.getBody().get( "clientId", String.class);
                String consumerOriginZone = allClaimsFromConsumerToken.getBody().get( "originZone", String.class);
                String consumerOriginStargate = allClaimsFromConsumerToken.getBody().get( "originStargate", String.class);
                String issuer = allClaimsFromConsumerToken.getBody().getIssuer();
                String azp = allClaimsFromConsumerToken.getBody().get("azp", String.class);
                System.out.println("ClientID: "+clientId);
                System.out.println("originZine: "+consumerOriginZone);
                System.out.println("originStargate: "+consumerOriginStargate);
                System.out.println("issuer: "+issuer);
                System.out.println("azp: "+azp);
            }
            Optional<Header> gwToken = httpRequest.getHeaders().stream().filter(header -> header.getName().equals(Constants.HEADER_LASTMILE_SECURITY_TOKEN)).findFirst();
            if(gwToken.isPresent()) {
                String jwtTokenSig = gwToken.get().getValues().get(0).getValue();
                String jwtToken = OauthTokenUtil.getTokenWithoutSignature(jwtTokenSig);
                Jwt<io.jsonwebtoken.Header, Claims> allClaimsFromConsumerToken = OauthTokenUtil.getAllClaimsFromConsumerToken(jwtToken);
                String clientId = allClaimsFromConsumerToken.getBody().get( "clientId", String.class);
                String consumerOriginZone = allClaimsFromConsumerToken.getBody().get( "originZone", String.class);
                String consumerOriginStargate = allClaimsFromConsumerToken.getBody().get( "originStargate", String.class);
                String issuer = allClaimsFromConsumerToken.getBody().getIssuer();
                String azp = allClaimsFromConsumerToken.getBody().get("azp", String.class);
                System.out.println("ClientID: "+clientId);
                System.out.println("originZine: "+consumerOriginZone);
                System.out.println("originStargate: "+consumerOriginStargate);
                System.out.println("issuer: "+issuer);
                System.out.println("azp: "+azp);
            }
            if(statusCode.isPresent()) {
                NottableString statusCodeString = statusCode.get().getValues().get(0);
                return response().withHeaders(httpRequestHeaders).withStatusCode(Integer.parseInt(statusCodeString.getValue()));
            } else {
                return notFoundResponse();
            }

        } else {
            return notFoundResponse();
        }
    }

}
