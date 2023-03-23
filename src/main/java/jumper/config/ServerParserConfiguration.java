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
            //happens after RemoveHeaderFilter, so tif_remote_issuer is already gone
            //String tif_remote_issuer = req.header(Constants.HEADER_ISSUER);
            String url = req.url();
            /*
            String consumerToken = req.header(Constants.HEADER_CONSUMER_TOKEN);
            String xTardisTraceId = req.header(Constants.HEADER_X_TARDIS_TRACE_ID);
            String xBusinessContext = req.header(Constants.HEADER_X_BUSINESS_CONTEXT);
            String xRequestId = req.header(Constants.HEADER_X_REQUEST_ID);
            String xCorrelationId = req.header(Constants.HEADER_X_CORRELATION_ID);
            String consumerOriginStargate = req.header(Constants.HEADER_X_ORIGIN_STARGATE);
            String envName = req.header(Constants.HEADER_ENVIRONMENT);
            String publisherId = req.header(Constants.HEADER_X_PUBSUB_PUBLISHER_ID);
            */
            String spanName = "Provider";
            if (req.header(Constants.HEADER_CONSUMER_TOKEN) != null) {
                spanName = "Gateway";
            }

            span.name("Outgoing Request: " + spanName);

            if (url != null) {
                span.tag("http.url", url);
            }
/*
            if (xTardisTraceId != null) {
                span.tag("x-tardis-traceid", xTardisTraceId);
            }

            if (consumerOriginStargate != null) {
                span.tag("origin-stargate", consumerOriginStargate);
            }

            if (xBusinessContext != null) {
                span.tag(Constants.HEADER_X_BUSINESS_CONTEXT, xBusinessContext);
            }

            if (xRequestId != null) {
                span.tag(Constants.HEADER_X_REQUEST_ID, xRequestId);
            }

            if (xCorrelationId != null) {
                span.tag(Constants.HEADER_X_CORRELATION_ID, xCorrelationId);
            }
            if (envName != null) {
                span.tag("environment.info", envName);
            }

            //callback
            if (publisherId != null) {
                span.tag("publisher", publisherId);

                String subscriber = req.header((Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID));
                if (subscriber != null) {
                    span.tag("subscriber", subscriber);
                }

            }
*/

        };
    }
}