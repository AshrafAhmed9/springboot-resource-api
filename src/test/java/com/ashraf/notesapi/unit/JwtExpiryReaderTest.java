package com.ashraf.notesapi.unit;

import com.ashraf.notesapi.security.JwtExpiryReader;
import com.ashraf.notesapi.support.FakeJwt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtExpiryReaderTest {

    private final JwtExpiryReader reader = new JwtExpiryReader();

    @Test
    void readsExpiryFromWellFormedToken() {
        long expectedExpiry = System.currentTimeMillis() / 1000 + 900;
        String token = FakeJwt.withExpiryInSeconds(900);

        long actual = reader.expiryEpochSeconds(token);

        assertThat(actual).isCloseTo(expectedExpiry, org.assertj.core.data.Offset.offset(2L));
    }

    @Test
    void returnsZeroForMalformedToken() {
        assertThat(reader.expiryEpochSeconds("not-a-jwt")).isZero();
    }

    @Test
    void returnsZeroForEmptyString() {
        assertThat(reader.expiryEpochSeconds("")).isZero();
    }
}
