package jumper.mocks;

import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.List;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

public class TestExpectationCallback implements ExpectationResponseCallback {
    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        if (httpRequest.getPath().getValue().endsWith("/callback")) {
            return response()
                    .withHeaders(httpRequest.getHeaders())
                    .withBody(httpRequest.getBodyAsString())
                    .withStatusCode(Integer.parseInt(httpRequest.getFirstQueryStringParameter("statusCode")));
        } else if (httpRequest.getPath().getValue().endsWith("/v1/events") && List.of("HEAD", "POST").contains(httpRequest.getMethod())) {
            return response()
                    .withStatusCode(Integer.parseInt(httpRequest.getFirstQueryStringParameter("statusCode")));
        } else {
            return notFoundResponse();
        }
    }

}
