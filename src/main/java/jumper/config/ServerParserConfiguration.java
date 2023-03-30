package jumper.config;

import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import jumper.Constants;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ServerParserConfiguration {


    @Bean(name = HttpClientResponseParser.NAME)
    HttpResponseParser httpResponseParser(){
        return ((response, context, span) -> {
            span.tag("http.status_code", String.valueOf(response.statusCode()));
        });
    }

    @Bean(name = HttpClientRequestParser.NAME)
    HttpRequestParser httpRequestParser() {
        return (req, context, span) -> {
            String url = req.url();
            String xTardisTraceId = req.header(Constants.HEADER_X_TARDIS_TRACE_ID);

            String spanName = "Provider";
            if (req.header(Constants.HEADER_CONSUMER_TOKEN) != null) {
                spanName = "Gateway";
            }

            span.name("Outgoing Request: " + spanName);

            if (url != null) {
                span.tag("http.url", url);
            }

            if (xTardisTraceId != null) {
                span.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
            }
        };
    }
}