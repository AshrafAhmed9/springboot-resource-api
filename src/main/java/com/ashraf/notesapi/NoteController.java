package com.ashraf.notesapi;

// Every HTTP endpoint this service exposes lives in this one file, so
// "what can I call" is answerable by reading top to bottom. The request
// and response shapes are nested right here since nothing else uses them.
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    public static class NoteRequest {
        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        private String title;
        private String body;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public record NoteResponse(Long id, Long ownerId, String title, String body, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static NoteResponse from(Note note) {
            return new NoteResponse(note.getId(), note.getOwnerId(), note.getTitle(), note.getBody(), note.getCreatedAt(), note.getUpdatedAt());
        }
    }

    private Long ownerId(Authentication authentication) {
        return Long.valueOf((String) authentication.getPrincipal());
    }

    @GetMapping("/api/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        List<String> roles = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return ResponseEntity.ok(Map.of("userId", authentication.getPrincipal(), "roles", roles));
    }

    @PostMapping("/api/notes")
    public ResponseEntity<NoteResponse> create(Authentication authentication, @Valid @RequestBody NoteRequest request) {
        Note note = noteService.create(ownerId(authentication), request.getTitle(), request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteResponse.from(note));
    }

    @GetMapping("/api/notes")
    public ResponseEntity<List<NoteResponse>> findAll(Authentication authentication) {
        List<NoteResponse> notes = noteService.findAllForOwner(ownerId(authentication)).stream().map(NoteResponse::from).toList();
        return ResponseEntity.ok(notes);
    }

    @GetMapping("/api/notes/{id}")
    public ResponseEntity<NoteResponse> findById(Authentication authentication, @PathVariable Long id) {
        Note note = noteService.findByIdForOwner(id, ownerId(authentication));
        return ResponseEntity.ok(NoteResponse.from(note));
    }

    @PutMapping("/api/notes/{id}")
    public ResponseEntity<NoteResponse> update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        Note note = noteService.update(id, ownerId(authentication), request.getTitle(), request.getBody());
        return ResponseEntity.ok(NoteResponse.from(note));
    }

    @DeleteMapping("/api/notes/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        noteService.delete(id, ownerId(authentication));
        return ResponseEntity.noContent().build();
    }
}
