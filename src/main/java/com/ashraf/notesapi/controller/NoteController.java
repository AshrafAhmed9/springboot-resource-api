package com.ashraf.notesapi.controller;

import com.ashraf.notesapi.dto.NoteRequest;
import com.ashraf.notesapi.dto.NoteResponse;
import com.ashraf.notesapi.entity.Note;
import com.ashraf.notesapi.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    private Long ownerId(Authentication authentication) {
        return Long.valueOf((String) authentication.getPrincipal());
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(Authentication authentication, @Valid @RequestBody NoteRequest request) {
        Note note = noteService.create(ownerId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteResponse.from(note));
    }

    @GetMapping
    public ResponseEntity<List<NoteResponse>> findAll(Authentication authentication) {
        List<NoteResponse> notes = noteService.findAllForOwner(ownerId(authentication)).stream()
                .map(NoteResponse::from)
                .toList();
        return ResponseEntity.ok(notes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> findById(Authentication authentication, @PathVariable Long id) {
        Note note = noteService.findByIdForOwner(id, ownerId(authentication));
        return ResponseEntity.ok(NoteResponse.from(note));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(Authentication authentication, @PathVariable Long id, @Valid @RequestBody NoteRequest request) {
        Note note = noteService.update(id, ownerId(authentication), request);
        return ResponseEntity.ok(NoteResponse.from(note));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long id) {
        noteService.delete(id, ownerId(authentication));
        return ResponseEntity.noContent().build();
    }
}
