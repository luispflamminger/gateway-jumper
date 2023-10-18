@upstream @iris
Feature: proper error message returned based on conditions

  Scenario: Consumer calls proxy route with no headers
    Given No headers are set
    When consumer calls the proxy route
    Then API consumer receives a 500 status code
    And error response contains msg "missing mandatory header remote_api_url" error "Internal Server Error" status 500

  Scenario: Consumer calls proxy route without Authorization token
    Given RealRoute headers without Authorization are set
    When consumer calls the proxy route
    Then API consumer receives a 500 status code
    And error response contains msg "Consumer token not provided, but expected" error "Internal Server Error" status 500

  @ignore
  Scenario: mesh IDP drops connection
    Given ProxyRoute headers are set
    And IDP set to drop connection
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 500 status code
    And error response contains msg "org.springframework.web.reactive.function.client.WebClientRequestException: Connection prematurely closed BEFORE response; nested exception is reactor.netty.http.client.PrematureCloseException: Connection prematurely closed BEFORE response" error "Internal Server Error" status 500

  Scenario: Consumer calls proxy route with jc with oauth, oauth wrong credential headers set
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And IDP set to provide externalInvalidAuth token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    And API consumer receives a 500 status code
    And error response contains msg "org.springframework.web.server.ResponseStatusException: 401 UNAUTHORIZED \"Failed to retrieve token from http://localhost:1081/external\"" error "Internal Server Error" status 500

