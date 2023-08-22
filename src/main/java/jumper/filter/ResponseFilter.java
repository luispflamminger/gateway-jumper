package jumper.filter;

import jumper.model.response.IncomingResponse;
import jumper.model.response.JumperInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static net.logstash.logback.argument.StructuredArguments.value;

@Component
@Slf4j
public class ResponseFilter extends AbstractGatewayFilterFactory<ResponseFilter.Config> {

	private final CurrentTraceContext currentTraceContext;

	public ResponseFilter(CurrentTraceContext currentTraceContext) {
        super(Config.class);
		this.currentTraceContext = currentTraceContext;
	}

	@Override
	public GatewayFilter apply(Config config) {
		return new OrderedGatewayFilter((exchange, chain) -> {

			return chain.filter(exchange).then(Mono.fromRunnable(() -> {

				WebFluxSleuthOperators.withSpanInScope(config.tracer, currentTraceContext, exchange, () -> {

					ServerHttpResponse response = exchange.getResponse();
					ServerHttpRequest request = exchange.getRequest();

					if (isLogLevelEnabled()) {
						JumperInfoResponse jumperInfoResponse = new JumperInfoResponse();
						IncomingResponse incomingResponse = new IncomingResponse();

						incomingResponse.setPath(request.getPath().toString());
						incomingResponse.setHttpStatusCode(response.getStatusCode().value());

						jumperInfoResponse.setIncomingResponse(incomingResponse);

						log.info("logging response: {}", value("jumperInfo", jumperInfoResponse));
					}

					Long contentLength = response.getHeaders().getContentLength();

					Span span = config.tracer.currentSpan();

					if (contentLength == null || contentLength.toString().equals("-1")) {
						span.tag("message.size_response", "0");
					} else {
						span.tag("message.size_response", contentLength.toString());
					}

					span.event("jrpf");

				});

			}));

		}, RequestFilter.REQUEST_FILTER_ORDER);
	}

	private boolean isLogLevelEnabled(){
		return log.isInfoEnabled();
	}

	/**
	 * Some configuration options for this filter
	 *
	 */
	public static class Config {

		private Tracer tracer;

		public Config(){}

		public Config(Tracer tracer) {
			this.tracer = tracer;
		}

		public void setTracer(Tracer tracer){
			this.tracer = tracer;
		}
	}

}
