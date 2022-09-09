package jumper.exception;

import java.util.*;

import jumper.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.server.*;

import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Slf4j
public class JsonErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler {

    @Value( "${spring.application.name}")
    private String applicationName;

    public JsonErrorWebExceptionHandler(ErrorAttributes errorAttributes,
                                        ResourceProperties resourceProperties,
                                        ErrorProperties errorProperties,
                                        ApplicationContext applicationContext) {
        super(errorAttributes, resourceProperties, errorProperties, applicationContext);
    }

    @Override
    protected Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        // Here the logic can actually be customized according to the exception type
        Throwable error = super.getError(request);

        MergedAnnotation<ResponseStatus> responseStatusAnnotation = MergedAnnotations
                .from(error.getClass(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).get(ResponseStatus.class);

        HttpStatus errorStatus = findHttpStatus(error, responseStatusAnnotation);
        Map<String, Object> errorAttributes = new HashMap<>(8);

        errorAttributes.put("service", applicationName);
        errorAttributes.put("timestamp", new Date());
        errorAttributes.put("message", (error.getMessage() != null) ? error.getMessage() : "");
        errorAttributes.put("error", errorStatus.getReasonPhrase());
        errorAttributes.put("status", errorStatus.value());
        errorAttributes.put("method", request.methodName());
        errorAttributes.put("traceId", (request.headers().firstHeader(Constants.HEADER_X_B3_TRACE_ID) != null) ?
                request.headers().firstHeader(Constants.HEADER_X_B3_TRACE_ID) : "");
        errorAttributes.put("tardisTraceId", (request.headers().firstHeader(Constants.HEADER_X_TARDIS_TRACE_ID) != null) ?
                request.headers().firstHeader(Constants.HEADER_X_TARDIS_TRACE_ID) : "");
        //for current jumper does not make sense
        //errorAttributes.put("path", request.path());

        //should also evaluate include options (stacktrace, message, bindingErrors)
        return errorAttributes;
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    @Override
    protected int getHttpStatus(Map<String, Object> errorAttributes) {
        int code = (int) errorAttributes.getOrDefault("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        log.debug("errorAttributes {}", errorAttributes);
        // Here you can actually customize the HTTP response code based on the attributes inside the errorAttributes
        /*
        if code != 500 error log is suppressed later

	protected void logError(ServerRequest request, ServerResponse response, Throwable throwable) {
		if (logger.isDebugEnabled()) {
			logger.debug(request.exchange().getLogPrefix() + formatError(throwable, request));
		}
		if (HttpStatus.resolve(response.rawStatusCode()) != null
				&& response.statusCode().equals(HttpStatus.INTERNAL_SERVER_ERROR)) {
			logger.error(LogMessage.of(() -> String.format("%s 500 Server Error for %s",
					request.exchange().getLogPrefix(), formatRequest(request))), throwable);
		}
	}
         */
        return code;
    }

    private HttpStatus findHttpStatus(Throwable error, MergedAnnotation<ResponseStatus> responseStatusAnnotation) {
        if (error instanceof ResponseStatusException) {
            return ((ResponseStatusException) error).getStatus();
        }

        /*
        io.netty.channel.ConnectTimeoutException
        io.netty.channel.AbstractChannel$AnnotatedConnectException
         */
        /*
        if (error instanceof java.net.ConnectException) {
            logError();
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        */

        return responseStatusAnnotation.getValue("code", HttpStatus.class).orElse(INTERNAL_SERVER_ERROR);
    }

//todo need to stabilize error logging before we start to overwrite status
    private void logError(){
       /* log.error(LogMessage.of(() -> String.format("%s 500 Server Error for %s",
                request.exchange().getLogPrefix(), formatRequest(request))), throwable);*/
    }

}
