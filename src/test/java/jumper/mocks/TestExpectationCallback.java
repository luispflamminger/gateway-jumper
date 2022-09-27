package jumper.mocks;

import org.mockserver.mock.action.ExpectationCallback;
import org.mockserver.model.*;

import java.util.List;
import java.util.Optional;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class TestExpectationCallback implements ExpectationCallback {
    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        if (httpRequest.getPath().getValue().endsWith("/callback")) {
            List<Header> httpRequestHeaders = httpRequest.getHeaders();
            List<Parameter> queryStringParameters = httpRequest.getQueryStringParameters();
            Optional<Parameter> statusCode = queryStringParameters.stream().filter(param -> param.getName().equals("statusCode")).findFirst();
            if(statusCode.isPresent()) {
                NottableString statusCodeString = statusCode.get().getValues().get(0);
                return response().withHeaders(httpRequestHeaders).withStatusCode(Integer.parseInt(statusCodeString.getValue()));
            } else {
                return notFoundResponse();
            }

        } else {
            return notFoundResponse();
        }
    }

}
