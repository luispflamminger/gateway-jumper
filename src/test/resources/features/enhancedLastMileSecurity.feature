@elms
Feature: Enhanced Last Mile Security

  Scenario: Consumer calls an API with EnhancedLastMileSecurity
    Given EnhancedLastMileSecurity is activated
    And APIs Provider will respond with a 200 status code
    When consumer calls the APIs
    Then API Provider receives MergedGatewayToken
    And APIs consumer receives a 200 status code
