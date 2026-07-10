package com.ashraf.notesapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Reads the {@code exp} claim from a JWT's payload without verifying the signature.
 * Signature verification is the Go auth service's job (via gRPC ValidateToken);
 * this is only used to bound the local cache TTL to the token's remaining lifetime.
 */
@Component
public class JwtExpiryReader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public long expiryEpochSeconds(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return 0;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
            return payload.has("exp") ? payload.get("exp").asLong() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
