package jumper.filter;

//import brave.Span;
//import brave.Tracer;
import jumper.Constants;
import jumper.model.response.IncomingResponse;
import jumper.model.response.JumperInfoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	Tracer tracer;

	@Autowired
	CurrentTraceContext currentTraceContext;

	public ResponseFilter() {
        super(Config.class);
    }

	@Override
	public GatewayFilter apply(Config config) {
		return new OrderedGatewayFilter((exchange, chain) -> {

			return chain.filter(exchange).then(Mono.fromRunnable(() -> {

				WebFluxSleuthOperators.withSpanInScope(tracer, currentTraceContext, exchange, () -> {

					ServerHttpResponse response = exchange.getResponse();
					ServerHttpRequest request = exchange.getRequest();

					Long contentLength = response.getHeaders().getContentLength();

					JumperInfoResponse jumperInfoResponse = new JumperInfoResponse();
					IncomingResponse incomingResponse = new IncomingResponse();
//				incomingResponse.setHost(response.getHeaders().getHost().toString());
					incomingResponse.setPath(request.getPath().toString());
					incomingResponse.setHttpStatusCode(response.getStatusCode().value());

					jumperInfoResponse.setIncomingResponse(incomingResponse);

					log.info("response", value("jumperInfo", jumperInfoResponse));

					Span span = this.tracer.currentSpan();

					span.tag("http.status_code",
							jumperInfoResponse.getIncomingResponse().getHttpStatusCode().toString());

					if (contentLength == null || contentLength.toString().equals("-1")) {
						span.tag("response.message.size", "0");
					} else {
						span.tag("response.message.size", contentLength.toString());
					}

					span.event("jrpf");
/*
					// Tracing - Start
					Span newSpan = this.tracer.nextSpan().name("Response Filter");


					String xTardisTraceId = request.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);
					String xCorrelationId = response.getHeaders().getFirst(Constants.HEADER_X_CORRELATION_ID);


					newSpan.tag("http.status_code",
							jumperInfoResponse.getIncomingResponse().getHttpStatusCode().toString());

					if (xTardisTraceId != null) {

						newSpan.tag("x-tardis-traceid", xTardisTraceId);
					}

					if (xCorrelationId != null) {
						newSpan.tag("x-correlation-id", xCorrelationId);
					}

					if (contentLength == null || contentLength.toString().equals("-1")) {

						newSpan.tag("message.size", "0");
					} else {

						newSpan.tag("message.size", contentLength.toString());
					}

					newSpan.finish();
					// Tracing - End
*/
				});

			}));

		}, RequestFilter.REQUEST_FILTER_ORDER);
	}

	/**
	 * Some configuration options for this filter
	 *
	 */
	public static class Config {

		private String name;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

}
