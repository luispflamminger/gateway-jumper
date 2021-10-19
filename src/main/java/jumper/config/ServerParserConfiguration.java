package jumper.config;

import brave.http.HttpRequestParser;
import jumper.Constants;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ServerParserConfiguration {

    @Bean(name = HttpClientRequestParser.NAME)
    HttpRequestParser httpRequestParser() {
        return (req, context, span) -> {
            //happens after RemoveHeaderFilter, so tif_remote_issuer is already gone
            //String tif_remote_issuer = req.header(Constants.HEADER_ISSUER);
            String consumerToken = req.header(Constants.HEADER_CONSUMER_TOKEN);
            String url = req.url();
            String xTardisTraceId = req.header(Constants.HEADER_X_TARDIS_TRACE_ID);
            String contentLength = req.header("Content-Length");
            String spanName = "Provider";

            if (consumerToken != null) {
                spanName = "Gateway";
            }

            if (xTardisTraceId != null) {
                span.tag("x-tardis-traceid", xTardisTraceId);
            }

            span.name("Outgoing Request: " + spanName);
            span.tag("http.method", req.method());
            span.tag("http.path", req.path());

            if (url != null) {

                span.tag("http.url", url);
            }

            if (contentLength == null) {

                span.tag("message.size", "0");
            } else {

                span.tag("message.size", contentLength);
            }

        };
    }
}