package com.ashraf.notesapi;

import com.ashraf.notesapi.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFilterIntegrationTest extends IntegrationTestBase {

    private static final String VALID_TOKEN = "valid-token";
    private static final String INVALID_TOKEN = "invalid-token";

    @BeforeEach
    void registerTokens() {
        fakeAuthService.registerValidToken(VALID_TOKEN, 1L, "user@test.com", "user");
        fakeAuthService.registerInvalidToken(INVALID_TOKEN, "token expired");
    }

    @Test
    void noTokenIsForbidden() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl() + "/api/notes", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(INVALID_TOKEN);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validTokenIsAuthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(VALID_TOKEN);

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.GET, new HttpEntity<>(headers), List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void authServiceOutageFailsClosedWith503() {
        fakeAuthService.simulateOutage(true);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("some-token-not-yet-cached");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotNull();
    }

    @Test
    void meEndpointReturnsAuthenticatedUserIdAndRoles() {
        fakeAuthService.registerValidToken("admin-token", 42L, "admin@test.com", "admin");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("admin-token");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("userId")).isEqualTo("42");
        assertThat(response.getBody().get("roles")).asList().containsExactly("ROLE_ADMIN");
    }
}
