package jumper.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class RemoveHeaderFilter extends RemoveRequestHeaderGatewayFilterFactory {
	
	public static final int REMOVE_HEADER_FILTER_ORDER = RequestFilter.REQUEST_FILTER_ORDER +1;
	
	public RemoveHeaderFilter() {
		super();
	}
	
	@Override
	public GatewayFilter apply(NameConfig config) {
		return new OrderedGatewayFilter((exchange, chain) -> {
			
			ServerHttpRequest request = exchange.getRequest().mutate()
					.headers(httpHeaders -> httpHeaders.remove(config.getName()))
					.build();
			
			return chain.filter(exchange.mutate()
					.request(request)
					.build());
				
		}, REMOVE_HEADER_FILTER_ORDER);
			
	}
}
