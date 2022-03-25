package jumper.filter;

import jumper.Constants;
import jumper.utilities.OauthTokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class SetSpectreAuthHeaderFilter extends SetRequestHeaderGatewayFilterFactory {
    @Value( "${jumper.issuer.url}")
    private String localIssuerUrl;

    private String defaultRealmName = Constants.DEFAULT_REALM;

    public GatewayFilter apply() {
        return (exchange, chain) -> {
            String value = "Bearer " + OauthTokenUtil.generateGatewayTokenForPublisher(localIssuerUrl + "/" + defaultRealmName);

            ServerHttpRequest request = exchange.getRequest().mutate().headers((httpHeaders) -> {
                httpHeaders.set(Constants.HEADER_AUTHORIZATION, value);
            }).build();


            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}
