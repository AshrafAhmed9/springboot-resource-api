package com.ashraf.notesapi.integration;

import com.ashraf.notesapi.dto.NoteRequest;
import com.ashraf.notesapi.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminEndpointIntegrationTest extends IntegrationTestBase {

    private static final String USER_TOKEN = "plain-user-token";
    private static final String ADMIN_TOKEN = "admin-token";

    @BeforeEach
    void registerTokens() {
        fakeAuthService.registerValidToken(USER_TOKEN, 10L, "user@test.com", "user");
        fakeAuthService.registerValidToken(ADMIN_TOKEN, 99L, "admin@test.com", "admin");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void nonAdminIsForbiddenFromAdminEndpoint() {
        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/admin/notes", HttpMethod.GET,
                new HttpEntity<>(authHeaders(USER_TOKEN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanSeeAllUsersNotes() {
        createNote(USER_TOKEN, "A regular user's note");

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/admin/notes", HttpMethod.GET,
                new HttpEntity<>(authHeaders(ADMIN_TOKEN)), List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    private void createNote(String token, String title) {
        NoteRequest request = new NoteRequest();
        request.setTitle(title);
        request.setBody("body");

        restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), Map.class
        );
    }
}
