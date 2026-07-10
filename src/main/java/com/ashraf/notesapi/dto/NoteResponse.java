package com.ashraf.notesapi.dto;

import com.ashraf.notesapi.entity.Note;

import java.time.LocalDateTime;

public record NoteResponse(
        Long id,
        Long ownerId,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getOwnerId(),
                note.getTitle(),
                note.getBody(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
