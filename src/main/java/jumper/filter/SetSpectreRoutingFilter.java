package jumper.filter;

import jumper.Constants;
import jumper.utilities.OauthTokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class SetSpectreRoutingFilter extends SetRequestHeaderGatewayFilterFactory {
    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    @Value( "${horizon.publishEventUrl}")
    private String publishEventUrl;

    public GatewayFilter apply() {
        return (exchange, chain) -> {
            ServerHttpRequest req = exchange.getRequest();

            //no environment info sent from kong, so we dig it from token
            String consumerToken;
            String publishEventPath;
            String envName = Constants.DEFAULT_REALM;
            if ((consumerToken = req.getHeaders().getFirst(Constants.HEADER_AUTHORIZATION)) != null) {
                envName = OauthTokenUtil.getClaimFromToken(consumerToken, "iss").replaceFirst(".*realms\\/", "");
            }

            try {
                URI uri = new URI(publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName));
                publishEventPath =  uri.getPath();
            }
            catch (URISyntaxException ex){
                throw new IllegalStateException("URISyntaxException while getting horizon url", ex);
            }

            //minimalistic token with correct issuer
            String spectreToken = "Bearer " + OauthTokenUtil.generateGatewayTokenForPublisher(localIssuerUrl + "/" + envName);

            //routing path is no longer fixed, so we set it here
            //placeholder is expected just on qa
            ServerHttpRequest request = req.mutate().headers((httpHeaders) -> {
                        httpHeaders.set(Constants.HEADER_AUTHORIZATION, spectreToken);
                    })
                    .path(publishEventPath)
                    .build();

            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, request.getURI());

            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}
