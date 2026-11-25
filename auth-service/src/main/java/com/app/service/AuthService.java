package com.app.service;

import com.app.response.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String tokenUri;
    private final String logoutUri;
    private final String clientId;
    private final String clientSecret;

    public AuthService(
            @Value("${keycloak.token-uri}") String tokenUri,
            @Value("${keycloak.issuer-uri}") String issuerUri,
            @Value("${keycloak.auth-client-id:${keycloak.client-id}}") String clientId,
            @Value("${keycloak.auth-client-secret:${keycloak.client-secret:}}") String clientSecret,
            Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
        this.tokenUri = tokenUri;
        this.logoutUri = issuerUri.replaceAll("/+$", "") + "/protocol/openid-connect/logout";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public TokenResponse login(String username, String password) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "password");
        form.add("username", username);
        form.add("password", password);
        TokenResponse tokens = requestTokens(form, "Invalid username or password");
        logger.info("Keycloak login succeeded");
        return tokens;
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        TokenResponse tokens = requestTokens(form, "Refresh token is invalid or expired");
        logger.info("Refresh token accepted; access token refreshed");
        return tokens;
    }

    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("refresh_token", refreshToken);
        try {
            restClient.post()
                    .uri(logoutUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            logger.info("Keycloak logout succeeded");
        } catch (RestClientResponseException exception) {
            logger.warn("Keycloak logout failed with HTTP status {}", exception.getStatusCode().value());
            throw keycloakException(exception, false, "Refresh token is invalid or expired");
        } catch (RestClientException exception) {
            logger.error("Could not connect to Keycloak during logout");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to Keycloak", exception);
        }
    }

    private TokenResponse requestTokens(MultiValueMap<String, String> form, String invalidGrantMessage) {
        try {
            KeycloakTokenResponse response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Keycloak returned an invalid token response");
            }
            return new TokenResponse(
                    response.accessToken(),
                    response.refreshToken(),
                    response.expiresIn(),
                    response.refreshExpiresIn(),
                    response.tokenType());
        } catch (RestClientResponseException exception) {
            if (invalidGrantMessage.startsWith("Refresh token")) {
                logger.warn("Security log: refresh token invalid or expired; Keycloak status={}",
                        exception.getStatusCode().value());
            } else {
                logger.warn("Security log: login credentials invalid; Keycloak status={}",
                        exception.getStatusCode().value());
            }
            throw keycloakException(exception, true, invalidGrantMessage);
        } catch (RestClientException exception) {
            logger.error("Could not connect to Keycloak during token request");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not connect to Keycloak", exception);
        }
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        if (!clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }
        return form;
    }

    private ResponseStatusException keycloakException(
            RestClientResponseException exception, boolean tokenRequest, String invalidGrantMessage) {
        if (tokenRequest && isInvalidGrant(exception.getResponseBodyAsString())) {
            return new ResponseStatusException(HttpStatus.UNAUTHORIZED, invalidGrantMessage);
        }
        if (exception.getStatusCode().is4xxClientError() && !tokenRequest) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak could not end the session");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak authentication request failed");
    }

    private boolean isInvalidGrant(String responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).get("error");
            return error != null && "invalid_grant".equals(error.asText());
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private record KeycloakTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_expires_in") long refreshExpiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType) {
    }
}
