package com.ashraf.notesapi.unit;

import com.ashraf.notesapi.dto.NoteRequest;
import com.ashraf.notesapi.entity.Note;
import com.ashraf.notesapi.exception.NoteNotFoundException;
import com.ashraf.notesapi.repository.NoteRepository;
import com.ashraf.notesapi.service.NoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NoteServiceTest {

    @Mock
    private NoteRepository noteRepository;

    private NoteService noteService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        noteService = new NoteService(noteRepository);
    }

    @Test
    void createSetsOwnerIdFromCaller() {
        NoteRequest request = new NoteRequest();
        request.setTitle("Title");
        request.setBody("Body");
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note created = noteService.create(42L, request);

        assertThat(created.getOwnerId()).isEqualTo(42L);
        assertThat(created.getTitle()).isEqualTo("Title");
    }

    @Test
    void findByIdForOwnerThrowsWhenNotOwned() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.findByIdForOwner(1L, 99L))
                .isInstanceOf(NoteNotFoundException.class);
    }

    @Test
    void updateThrowsWhenNotOwnedInsteadOfLeakingExistence() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());
        NoteRequest request = new NoteRequest();
        request.setTitle("Hijack attempt");

        assertThatThrownBy(() -> noteService.update(1L, 99L, request))
                .isInstanceOf(NoteNotFoundException.class);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void deleteThrowsWhenNotOwned() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.delete(1L, 99L))
                .isInstanceOf(NoteNotFoundException.class);

        verify(noteRepository, never()).deleteByIdAndOwnerId(any(), any());
    }
}
