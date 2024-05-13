# SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
#
# SPDX-License-Identifier: Apache-2.0

@upstream @iris
Feature: proper authorization token reaches provider endpoint if x-token-exchange set

  Scenario: Consumer calls proxy route with XtokenExchange Header and currentZone space
    Given RealRoute headers are set with x-token-exchange
    And current zone is "space"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    
  Scenario: Consumer calls proxy route with XtokenExchange Header and currentZone canis
    Given RealRoute headers are set with x-token-exchange
    And current zone is "canis"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    
  Scenario: Consumer calls proxy route with XtokenExchange Header and currentZone aries
    Given RealRoute headers are set with x-token-exchange
    And current zone is "aries"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    
  Scenario: Consumer calls proxy route with XtokenExchange Header and currentZone cetus
    Given RealRoute headers are set with x-token-exchange
    And current zone is "cetus"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneToken
    
  Scenario: Consumer calls proxy route with XtokenExchange Header and currentZone aws
    Given RealRoute headers are set with x-token-exchange
    And current zone is "aws"
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives authorization OneToken
    
 Scenario: Consumer calls proxy route with proxy route headers with xTokenExchange header and currentZone space, mesh token sent
    Given ProxyRoute headers are set with x-token-exchange
    And current zone is "space"
    And IDP set to provide internal token
    And API provider set to respond with a 200 status code
    When consumer calls the proxy route
    Then API Provider receives default bearer authorization headers
    Then API Provider receives authorization MeshToken
    And API consumer receives a 200 status code
    
 Scenario: Consumer calls proxy route with real route headers with xTokenExchange header and currentZone space, jc with consumer and provider specific basic auth provided, xTokenExchange sent
    Given RealRoute headers are set with x-token-exchange
    And current zone is "space"
    And API provider set to respond with a 200 status code
    And jumperConfig basic auth "consumer and provider" set
    When consumer calls the proxy route
    Then API Provider receives authorization XTokenExchangeHeader
    And API consumer receives a 200 status code