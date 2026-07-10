package com.ashraf.notesapi.integration;

import com.ashraf.notesapi.dto.NoteRequest;
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

class NoteCrudIntegrationTest extends IntegrationTestBase {

    private static final String USER_TOKEN = "user-token";
    private static final String OTHER_USER_TOKEN = "other-user-token";

    @BeforeEach
    void registerTokens() {
        fakeAuthService.registerValidToken(USER_TOKEN, 1L, "user@test.com", "user");
        fakeAuthService.registerValidToken(OTHER_USER_TOKEN, 2L, "other@test.com", "user");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void createThenFetchNote() {
        NoteRequest request = new NoteRequest();
        request.setTitle("My first note");
        request.setBody("Some content");

        ResponseEntity<Map> createResponse = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(USER_TOKEN)), Map.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Integer id = (Integer) createResponse.getBody().get("id");
        assertThat(id).isNotNull();

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(USER_TOKEN)), Map.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().get("title")).isEqualTo("My first note");
    }

    @Test
    void listReturnsOnlyOwnNotes() {
        createNote(USER_TOKEN, "User1 note");
        createNote(OTHER_USER_TOKEN, "User2 note");

        ResponseEntity<List> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.GET,
                new HttpEntity<>(authHeaders(USER_TOKEN)), List.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void updateNote() {
        Integer id = createNote(USER_TOKEN, "Original title");

        NoteRequest update = new NoteRequest();
        update.setTitle("Updated title");
        update.setBody("Updated body");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.PUT,
                new HttpEntity<>(update, authHeaders(USER_TOKEN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Updated title");
    }

    @Test
    void deleteNote() {
        Integer id = createNote(USER_TOKEN, "To be deleted");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(USER_TOKEN)), Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> getResponse = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(USER_TOKEN)), Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotAccessOtherUsersNote() {
        Integer id = createNote(USER_TOKEN, "Private note");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.GET,
                new HttpEntity<>(authHeaders(OTHER_USER_TOKEN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotUpdateOtherUsersNote() {
        Integer id = createNote(USER_TOKEN, "Private note");

        NoteRequest update = new NoteRequest();
        update.setTitle("Hijacked");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.PUT,
                new HttpEntity<>(update, authHeaders(OTHER_USER_TOKEN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotDeleteOtherUsersNote() {
        Integer id = createNote(USER_TOKEN, "Private note");

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/api/notes/" + id, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders(OTHER_USER_TOKEN)), Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsBlankTitle() {
        NoteRequest request = new NoteRequest();
        request.setTitle("");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(USER_TOKEN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Integer createNote(String token, String title) {
        NoteRequest request = new NoteRequest();
        request.setTitle(title);
        request.setBody("body");

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/notes", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), Map.class
        );
        return (Integer) response.getBody().get("id");
    }
}
