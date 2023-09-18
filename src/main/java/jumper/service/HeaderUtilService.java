package jumper.service;

import jumper.Constants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

@Service
public class HeaderUtilService {

    public String getFirstValueFromHeaderField(ServerHttpRequest request, String headerName) {
        return request.getHeaders().getFirst(headerName);
    }

    public String getLastValueFromHeaderField(ServerHttpRequest request, String headerName) {
        return request.getHeaders().getValuesAsList(headerName)
                .stream()
                .reduce((first, last) -> last)
                .orElse(null);
    }

    public void addHeader(ServerWebExchange exchange, String headerName, String headerValue) {
        exchange.getRequest()
                .mutate()
                .header(headerName, headerValue)
                .build();
    }

    public void removeHeader(ServerWebExchange exchange, String headerName) {
        exchange.getRequest()
                .mutate()
                .headers(httpHeaders -> httpHeaders.remove(headerName))
                .build();
    }

    public void rewriteXForwardedHeader( ServerWebExchange exchange) {
        addHeader(exchange, Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT);
        addHeader(exchange, Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);


    }
}
