@default
Feature: expected authorization token created

  ################ one token ################
  Scenario: Consumer calls jumper with real route headers, OneToken scenario
    Given RealRoute headers are set
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives default headers
    Then API Provider receives token OneToken
    And API consumer receives a 200 status code

  Scenario: Horizon calls jumper with pub/sub info, OneToken contains pub/sub info
    Given RealRoute headers are set
    And pub sub contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token OneTokenWithPubSub
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper with security scopes, OneToken contains scopes
    Given RealRoute headers are set
    And jumperConfig with scopes set
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token OneTokenWithScopes
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper with iris token containing aud, OneToken contains audience
    Given RealRoute headers are set
    And authorization token with aud set
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token OneTokenWithAud
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper with real route headers and realm header contains several values, correct issuer in OneToken
    Given RealRoute headers are set
    And several realm fields are contained in the header
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives default headers
    Then API Provider receives token OneToken
    And API consumer receives a 200 status code

  ################ mesh ################
  Scenario: Consumer calls jumper with proxy route headers, mesh scenario
    Given ProxyRoute headers are set
    And IDP set to provide internal token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives default headers
    Then API Provider receives token MeshToken
    And API consumer receives a 200 status code

  ################ external ################
  Scenario: Consumer calls jumper route using oauth, but client credentials not defined
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And API provider set to respond with a 200 status code
    When consumer calls the API
    And API consumer receives a 401 status code

  Scenario: Consumer calls jumper route using configured oauth, external authorization scenario
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "default" set
    And IDP set to provide external token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using header oauth, external authorization scenario
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And spacegate oauth headers set
    And IDP set to provide externalHeader token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalHeader
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using configured oauth with scope, external authorization scenario
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "scoped" set
    And IDP set to provide externalScoped token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using configured and header oauth with scope, external authorization scenario with header precedence
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "scoped" set
    And spacegate oauth scoped headers set
    And IDP set to provide externalHeaderScoped token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalHeader
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using configured oauth including explicit grant type, external authorization scenario with basic auth
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type client_credentials" set
    And IDP set to provide externalBasicAuthCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using configured oauth including password grant type, external authorization scenario with username/password
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type password" set
    And IDP set to provide externalUsernamePasswordCredentials token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalConfigured
    And API consumer receives a 200 status code

  Scenario: Consumer calls jumper route using configured oauth including password grant type, external authorization scenario with username/password only
    Given RealRoute headers are set
    And oauth tokenEndpoint set
    And jumperConfig oauth "grant_type password only" set
    And IDP set to provide externalUsernamePasswordCredentialsOnly token
    And API provider set to respond with a 200 status code
    When consumer calls the API
    Then API Provider receives token ExternalConfigured
    And API consumer receives a 200 status code