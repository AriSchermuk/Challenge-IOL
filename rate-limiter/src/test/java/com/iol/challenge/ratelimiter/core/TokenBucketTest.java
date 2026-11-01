package com.iol.challenge.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TokenBucketTest {
    @Test void burstFractionalRefillAndRejectionRetention() {
        var bucket = new TokenBucket(3, 2);
        for (int i = 0; i < 3; i++) assertEquals(Decision.allowed(), bucket.acquire(0));
        assertEquals(Decision.limited(500_000_000), bucket.acquire(0));
        assertEquals(Decision.limited(250_000_000), bucket.acquire(250_000_000));
        assertEquals(Decision.limited(1), bucket.acquire(499_999_999));
        assertEquals(Decision.allowed(), bucket.acquire(500_000_000));
        assertEquals(Decision.limited(500_000_000), bucket.acquire(500_000_000));
    }
    @Test void capDiscardsExcessIdleTimeAndHandlesHugeDurations() {
        var bucket = new TokenBucket(1_000_000, 1_000_000);
        assertEquals(Decision.allowed(), bucket.acquire(0));
        assertTrue(bucket.full(Long.MAX_VALUE));
        assertEquals(Decision.allowed(), bucket.acquire(Long.MAX_VALUE));
        for (int i = 1; i < 1_000_000; i++) assertEquals(Decision.allowed(), bucket.acquire(Long.MAX_VALUE));
        assertEquals(Decision.limited(1000), bucket.acquire(Long.MAX_VALUE));
    }
    @Test void backwardsTimeDoesNotGrantOrDoubleCountCredit() {
        var bucket = new TokenBucket(1, 1);
        bucket.acquire(100);
        assertEquals(Decision.limited(1_000_000_000), bucket.acquire(50));
        assertEquals(Decision.limited(999_999_999), bucket.acquire(101));
    }
    @Test void monotonicWrapAndUpwardRounding() {
        var bucket = new TokenBucket(1, 3);
        bucket.acquire(Long.MAX_VALUE - 9);
        assertEquals(Decision.limited(333_333_324), bucket.acquire(Long.MIN_VALUE));
        assertEquals(Decision.allowed(), bucket.acquire(Long.MIN_VALUE + 333_333_324));
    }
    @Test void fullBucketCannotBankTimeBeforeFirstConsumption() {
        var bucket = new TokenBucket(1, 1);
        assertTrue(bucket.full(0));
        assertTrue(bucket.full(9_000_000_000L));
        assertEquals(Decision.allowed(), bucket.acquire(9_000_000_000L));
        assertEquals(Decision.limited(1_000_000_000), bucket.acquire(9_000_000_000L));
    }
}
