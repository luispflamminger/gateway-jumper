package jumper.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RemoveHeaderFilter extends AbstractGatewayFilterFactory<RemoveHeaderFilter.Config> {
	
	public static final int REMOVE_HEADER_FILTER_ORDER = RequestFilter.REQUEST_FILTER_ORDER +1;
	
	public RemoveHeaderFilter() {
		super(Config.class);
	}
	
	@Override
	public GatewayFilter apply(Config config) {
		return new OrderedGatewayFilter((exchange, chain) -> {

			ServerHttpRequest request = exchange.getRequest().mutate()
					.headers(httpHeaders -> config.getHeaders().forEach(httpHeaders::remove))
					.build();

			return chain.filter(exchange.mutate()
					.request(request)
					.build());

		}, REMOVE_HEADER_FILTER_ORDER);
			
	}

	@Getter
	@Setter
	public static class Config {
		private Set<String> headers;
	}
}
