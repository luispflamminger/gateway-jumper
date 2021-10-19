package jumper.filter;

import static net.logstash.logback.argument.StructuredArguments.value;

import jumper.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import brave.Span;
import brave.Tracer;
import jumper.model.response.IncomingResponse;
import jumper.model.response.JumperInfoResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ResponseFilter extends AbstractGatewayFilterFactory<ResponseFilter.Config> {

	@Autowired
	Tracer tracer;
	
	public ResponseFilter() {
        super(Config.class);
    }

	@Override
	public GatewayFilter apply(Config config) {
		return new OrderedGatewayFilter((exchange, chain) -> {

			return chain.filter(exchange).then(Mono.fromRunnable(() -> {

				ServerHttpResponse response = exchange.getResponse();
				ServerHttpRequest request = exchange.getRequest();

				String debugHeader = request.getHeaders().getFirst(Constants.HEADER_DEBUG_RESPONSE_HEADER);
				Long contentLength = response.getHeaders().getContentLength();

				JumperInfoResponse jumperInfoResponse = new JumperInfoResponse();
				IncomingResponse incomingResponse = new IncomingResponse();
//				incomingResponse.setHost(response.getHeaders().getHost().toString());
				incomingResponse.setPath(request.getPath().toString());
				incomingResponse.setHttpStatusCode(response.getStatusCode().value());

//		        if (debugHeader != null && !debugHeader.isEmpty() && debugHeader.equals("true"))
//		        {
//		            incomingResponse.setOriginHeaderResponse(response.getHeaders().values());
//		        }

				jumperInfoResponse.setIncomingResponse(incomingResponse);

				log.info("response", value("jumperInfo", jumperInfoResponse));

				// Tracing - Start
				Span newSpan = this.tracer.nextSpan().name("Response Filter");
				try (Tracer.SpanInScope ws = this.tracer.withSpanInScope(newSpan.start())) {

					String xTardisTraceId = request.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);

					newSpan.tag("http.status_code",
							jumperInfoResponse.getIncomingResponse().getHttpStatusCode().toString());

					if (xTardisTraceId != null) {

						newSpan.tag("x-tardis-traceid", xTardisTraceId);
					}

					if (contentLength == null || contentLength.toString().equals("-1")) {

						newSpan.tag("message.size", "0");
					} else {

						newSpan.tag("message.size", contentLength.toString());
					}

				} finally {

					newSpan.finish();
				}
				// Tracing - End

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
