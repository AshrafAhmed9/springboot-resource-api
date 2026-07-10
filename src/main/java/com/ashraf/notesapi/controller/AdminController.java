package com.ashraf.notesapi.controller;

import com.ashraf.notesapi.dto.NoteResponse;
import com.ashraf.notesapi.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final NoteService noteService;

    public AdminController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/notes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NoteResponse>> findAll() {
        List<NoteResponse> notes = noteService.findAllForAdmin().stream()
                .map(NoteResponse::from)
                .toList();
        return ResponseEntity.ok(notes);
    }
}
