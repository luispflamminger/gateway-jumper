@elms
Feature: Enhanced Last Mile Security

  Scenario: Consumer calls an API with EnhancedLastMileSecurity
    Given EnhancedLastMileSecurity is activated
    And API provider will respond with a 200 status code
    When consumer calls the API
    Then API Provider receives MergedGatewayToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with EnhancedLastMileSecurity and realm header contains several values
    Given EnhancedLastMileSecurity is activated
    And several realm fields are contained in the header
    And API provider will respond with a 200 status code
    When consumer calls the API
    Then API Provider receives MergedGatewayToken
    And API consumer receives a 200 status code

  Scenario: Consumer calls an API with EnhancedLastMileSecurity
    Given EnhancedLastMileSecurity is activated
    And API provider will respond with a 401 status code
    When consumer calls the API
    Then API Provider receives MergedGatewayToken
    And API consumer receives a 401 status code

  Scenario: Consumer calls an API with EnhancedLastMileSecurity
    Given EnhancedLastMileSecurity is activated
    And API provider will respond with a 503 status code
    When consumer calls the API
    Then API Provider receives MergedGatewayToken
    And API consumer receives a 503 status code

  Scenario: Consumer calls an API with lastMileSecurity and Provider will have a timeout
    Given EnhancedLastMileSecurity is activated
    And API provider will respond with a 200 status code
    When consumer calls the API and runs into timeout
    Then API consumer receives a 504 status code

  Scenario: Consumer calls an API with lastMileSecurity and connection will be dropped
    Given EnhancedLastMileSecurity is activated
    And API provider will respond with a 200 status code
    When consumer calls the API and connection is dropped
    Then API consumer receives a 500 status code

  Scenario: Consumer calls an API with EnhancedLastMileSecurity and oauth scope
    Given EnhancedLastMileSecurity is activated
    And JumperConfig security scope is added
    And API provider will respond with a 200 status code
    When consumer calls the API
    Then API Provider receives MergedGatewayToken
    And Authorization token contains scope claim
    And API consumer receives a 200 status code
