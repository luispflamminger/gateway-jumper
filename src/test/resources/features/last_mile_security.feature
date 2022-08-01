Feature: Last Mile Security

  Scenario: Consumer calls an API with lastMileSecurity
    Given lastMileSecurity is activated
    And API Provider will respond with a 200 status code
    When consumer calls the API
    Then API Provider receives AccessToken and GatewayToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 401
    Given lastMileSecurity is activated
    And API Provider will respond with a 401 status code
    When consumer calls the API
    Then API Provider receives AccessToken and NO_GatewayToken
    And API consumer receives a 401 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider respond with a 503
    Given lastMileSecurity is activated
    And API Provider will respond with a 503 status code
    When consumer calls the API
    Then API Provider receives AccessToken and GatewayToken
    And API consumer receives a 503 status code