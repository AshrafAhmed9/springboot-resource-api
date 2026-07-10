package com.ashraf.notesapi.security;

public record CachedValidation(long userId, String email, String role) {
}
