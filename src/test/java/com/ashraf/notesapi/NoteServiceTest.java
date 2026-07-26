package com.ashraf.notesapi;

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
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note created = noteService.create(42L, "Title", "Body");

        assertThat(created.getOwnerId()).isEqualTo(42L);
        assertThat(created.getTitle()).isEqualTo("Title");
    }

    @Test
    void findByIdForOwnerThrowsWhenNotOwned() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.findByIdForOwner(1L, 99L))
                .isInstanceOf(NoteService.NoteNotFoundException.class);
    }

    @Test
    void updateThrowsWhenNotOwnedInsteadOfLeakingExistence() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.update(1L, 99L, "Hijack attempt", "body"))
                .isInstanceOf(NoteService.NoteNotFoundException.class);

        verify(noteRepository, never()).save(any());
    }

    @Test
    void deleteThrowsWhenNotOwned() {
        when(noteRepository.findByIdAndOwnerId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.delete(1L, 99L))
                .isInstanceOf(NoteService.NoteNotFoundException.class);

        verify(noteRepository, never()).deleteByIdAndOwnerId(any(), any());
    }
}
