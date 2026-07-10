package com.ashraf.notesapi.service;

import com.ashraf.notesapi.dto.NoteRequest;
import com.ashraf.notesapi.entity.Note;
import com.ashraf.notesapi.exception.NoteNotFoundException;
import com.ashraf.notesapi.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @Transactional
    public Note create(Long ownerId, NoteRequest request) {
        Note note = new Note();
        note.setOwnerId(ownerId);
        note.setTitle(request.getTitle());
        note.setBody(request.getBody());
        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public List<Note> findAllForOwner(Long ownerId) {
        return noteRepository.findAllByOwnerId(ownerId);
    }

    @Transactional(readOnly = true)
    public Note findByIdForOwner(Long id, Long ownerId) {
        return noteRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NoteNotFoundException(id));
    }

    @Transactional
    public Note update(Long id, Long ownerId, NoteRequest request) {
        Note note = findByIdForOwner(id, ownerId);
        note.setTitle(request.getTitle());
        note.setBody(request.getBody());
        return noteRepository.save(note);
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        findByIdForOwner(id, ownerId);
        noteRepository.deleteByIdAndOwnerId(id, ownerId);
    }

    @Transactional(readOnly = true)
    public List<Note> findAllForAdmin() {
        return noteRepository.findAll();
    }
}
