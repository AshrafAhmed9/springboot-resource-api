package com.ashraf.notesapi;

import com.ashraf.notesapi.support.FakeJwt;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCacheTest {

    private final TokenCache tokenCache = new TokenCache();

    @Test
    void ttlIsBoundedToSixtySecondsEvenForLongLivedTokens() {
        long farFutureExpiry = System.currentTimeMillis() / 1000 + 3600; // 1 hour
        long ttlNanos = TokenCache.ttlNanosFor(farFutureExpiry, 60);

        assertThat(TimeUnit.NANOSECONDS.toSeconds(ttlNanos)).isEqualTo(60);
    }

    @Test
    void ttlMatchesRemainingLifetimeWhenShorterThanSixtySeconds() {
        long soonExpiry = System.currentTimeMillis() / 1000 + 10;
        long ttlNanos = TokenCache.ttlNanosFor(soonExpiry, 60);

        assertThat(TimeUnit.NANOSECONDS.toSeconds(ttlNanos)).isBetween(8L, 10L);
    }

    @Test
    void ttlIsZeroForAlreadyExpiredToken() {
        long pastExpiry = System.currentTimeMillis() / 1000 - 10;
        long ttlNanos = TokenCache.ttlNanosFor(pastExpiry, 60);

        assertThat(ttlNanos).isZero();
    }

    @Test
    void readsExpiryFromWellFormedToken() {
        long expectedExpiry = System.currentTimeMillis() / 1000 + 900;
        String token = FakeJwt.withExpiryInSeconds(900);

        long actual = tokenCache.expiryEpochSeconds(token);

        assertThat(actual).isCloseTo(expectedExpiry, org.assertj.core.data.Offset.offset(2L));
    }

    @Test
    void returnsZeroForMalformedToken() {
        assertThat(tokenCache.expiryEpochSeconds("not-a-jwt")).isZero();
    }

    @Test
    void returnsZeroForEmptyString() {
        assertThat(tokenCache.expiryEpochSeconds("")).isZero();
    }
}
