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
            String xB3TraceId = req.header(Constants.HEADER_X_B3_TRACE_ID);
            String xBusinessContext = req.header( Constants.HEADER_X_BUSINESS_CONTEXT);
            String xRequestId = req.header( Constants.HEADER_X_REQUEST_ID);
            String xCorrelationId = req.header( Constants.HEADER_X_CORRELATION_ID);
            String consumerOriginStargate = req.header(Constants.HEADER_X_ORIGIN_STARGATE);
            String envName = req.header(Constants.HEADER_ENVIRONMENT);
            String publisherId = req.header(Constants.HEADER_X_PUBLISHER_ID);
            String token;

            String spanName = "Provider";
            if (consumerToken != null) {
                spanName = "Gateway";
                token = consumerToken;
            }
            else{
                token = req.header("Authorization");
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

            if (contentLength == null || contentLength.toString().equals( "-1")) {

                span.tag("message.size", "0");
            } else {

                span.tag("message.size", contentLength);
            }

            if( consumerOriginStargate != null)
            {
                span.tag( "origin-stargate", consumerOriginStargate);
            }

            if( xB3TraceId != null)
            {

                span.tag( Constants.HEADER_X_B3_TRACE_ID, xB3TraceId);
            }

            if( xBusinessContext != null)
            {

                span.tag( Constants.HEADER_X_BUSINESS_CONTEXT, xBusinessContext);
            }

            if( xRequestId != null)
            {

                span.tag( Constants.HEADER_X_REQUEST_ID, xRequestId);
            }

            if( xCorrelationId != null)
            {

                span.tag( Constants.HEADER_X_CORRELATION_ID, xCorrelationId);
            }
            if( envName != null)
            {

                span.tag( "environment.info", envName);
            }

            //callback
            if (publisherId != null){
                span.tag("publisher", publisherId);

                String subscriptionId = req.header(Constants.HEADER_X_SUBSCRIPTION_ID);
                if (subscriptionId != null){
                    span.tag("subscription-id", subscriptionId);
                }

            }
            //not callback, assume request-response
            else {
                /* increase cpu too much
                if (token != null) {
                    String consumer = OauthTokenUtil.getConsumerFromToken(token);
                    if (consumer != null) {
                        span.tag("consumer", consumer);
                    }

                    String apiBasePath = OauthTokenUtil.getClaimFromToken(token, "requestPath");
                    if (apiBasePath != null) {
                        span.tag("peer.service", apiBasePath.substring(1, apiBasePath.length() - 1).replace("/", "-"));
                    }
                }

                 */
            }

        };
    }
}