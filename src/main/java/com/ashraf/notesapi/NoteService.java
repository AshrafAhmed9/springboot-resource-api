package com.ashraf.notesapi;

// Ownership is enforced here, not in the controller, using
// findByIdAndOwnerId — so a note that exists but belongs to someone else
// looks identical to a note that doesn't exist (404, never 403). That
// means a caller can't probe which note IDs exist by watching status codes.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public static class NoteNotFoundException extends RuntimeException {
        public NoteNotFoundException(Long id) {
            super("Note not found: " + id);
        }
    }

    @Transactional
    public Note create(Long ownerId, String title, String body) {
        Note note = new Note();
        note.setOwnerId(ownerId);
        note.setTitle(title);
        note.setBody(body);
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
    public Note update(Long id, Long ownerId, String title, String body) {
        Note note = findByIdForOwner(id, ownerId);
        note.setTitle(title);
        note.setBody(body);
        return noteRepository.save(note);
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        findByIdForOwner(id, ownerId);
        noteRepository.deleteByIdAndOwnerId(id, ownerId);
    }
}
