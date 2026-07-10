package com.ashraf.notesapi.unit;

import com.ashraf.notesapi.config.CacheConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void ttlIsBoundedToSixtySecondsEvenForLongLivedTokens() {
        long farFutureExpiry = System.currentTimeMillis() / 1000 + 3600; // 1 hour
        long ttlNanos = CacheConfig.ttlNanosFor(farFutureExpiry, 60);

        assertThat(TimeUnit.NANOSECONDS.toSeconds(ttlNanos)).isEqualTo(60);
    }

    @Test
    void ttlMatchesRemainingLifetimeWhenShorterThanSixtySeconds() {
        long soonExpiry = System.currentTimeMillis() / 1000 + 10;
        long ttlNanos = CacheConfig.ttlNanosFor(soonExpiry, 60);

        assertThat(TimeUnit.NANOSECONDS.toSeconds(ttlNanos)).isBetween(8L, 10L);
    }

    @Test
    void ttlIsZeroForAlreadyExpiredToken() {
        long pastExpiry = System.currentTimeMillis() / 1000 - 10;
        long ttlNanos = CacheConfig.ttlNanosFor(pastExpiry, 60);

        assertThat(ttlNanos).isZero();
    }
}
