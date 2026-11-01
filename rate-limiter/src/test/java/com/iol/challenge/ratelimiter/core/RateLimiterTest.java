package com.iol.challenge.ratelimiter.core;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
    @Test void validatesAllCoreInputs() {
        for (int invalid : new int[]{0, -1, 1_000_001}) {
            assertThrows(IllegalArgumentException.class, () -> new RateLimiter(invalid, 1, 1, () -> 0));
            assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, invalid, 1, () -> 0));
            assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, 1, invalid, () -> 0));
        }
        assertThrows(NullPointerException.class, () -> new RateLimiter(1, 1, 1, null));
        assertThrows(NullPointerException.class, () -> new RateLimiter(1, 1, 1, () -> 0).acquire(null));
    }
    @Test void isolationBoundAndSafeReclamation() {
        var time = new AtomicLong();
        var limiter = new RateLimiter(1, 1, 2, time::get);
        assertEquals(Decision.allowed(), limiter.acquire("a"));
        assertEquals(Decision.allowed(), limiter.acquire("b"));
        assertEquals(Decision.saturated(), limiter.acquire("c"));
        assertEquals(Decision.limited(1_000_000_000), limiter.acquire("a"));
        time.set(1_000_000_000);
        assertEquals(Decision.allowed(), limiter.acquire("c"));
        assertEquals(Decision.limited(1_000_000_000), limiter.acquire("c"));
        time.set(2_000_000_000L);
        assertEquals(Decision.allowed(), limiter.acquire("a"));
        assertEquals(Decision.allowed(), limiter.acquire("b"));
        assertEquals(Decision.limited(1_000_000_000), limiter.acquire("a"));
    }
    @Test void simultaneousFirstUseCannotOverspend() throws Exception {
        var limiter = new RateLimiter(10, 1, 10, () -> 0);
        assertEquals(10, race(64, i -> limiter.acquire("same")).stream()
                .filter(d -> d.status() == Decision.Status.ALLOWED).count());
    }
    @Test void simultaneousCreationCannotExceedRegistryBound() throws Exception {
        var limiter = new RateLimiter(1, 1, 7, () -> 0);
        var results = race(48, i -> limiter.acquire("client" + i));
        assertEquals(7, results.stream().filter(d -> d.status() == Decision.Status.ALLOWED).count());
        assertEquals(41, results.stream().filter(d -> d.status() == Decision.Status.SATURATED).count());
    }
    @Test void differentClientsProgressWhileOneIsBlockedAndPinnedEntriesCannotBeRemoved() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pause = new AtomicBoolean();
        LongSupplier clock = () -> {
            if (Thread.currentThread().getName().equals("blocked") && pause.get()) {
                entered.countDown();
                await(release);
            }
            return 1_000_000_000;
        };
        var limiter = new RateLimiter(2, 1, 2, clock);
        limiter.acquire("a");
        limiter.acquire("b");
        pause.set(true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var blocked = executor.submit(() -> {
                Thread.currentThread().setName("blocked");
                return limiter.acquire("a");
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(Decision.allowed(), executor.submit(() -> limiter.acquire("b")).get(5, TimeUnit.SECONDS));
                assertEquals(Decision.saturated(), executor.submit(() -> limiter.acquire("c")).get(5, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            assertEquals(Decision.allowed(), blocked.get(5, TimeUnit.SECONDS));
            assertEquals(Decision.limited(1_000_000_000), limiter.acquire("a"));
        }
    }
    @Test void failedClockDoesNotLeakReferencesOrLocks() {
        var calls = new AtomicInteger();
        var limiter = new RateLimiter(1, 1, 1, () -> {
            if (calls.getAndIncrement() == 0) throw new IllegalStateException("clock failed");
            return 0;
        });
        assertThrows(IllegalStateException.class, () -> limiter.acquire("a"));
        assertEquals(Decision.allowed(), limiter.acquire("b"));
        assertEquals(Decision.saturated(), limiter.acquire("a"));
    }
    @Test void failedReclamationReleasesRegistryAndEntryLocks() {
        var fail = new AtomicBoolean();
        var time = new AtomicLong();
        var limiter = new RateLimiter(1, 1, 1, () -> {
            if (fail.get()) throw new IllegalStateException("clock failed");
            return time.get();
        });
        limiter.acquire("a");
        fail.set(true);
        assertThrows(IllegalStateException.class, () -> limiter.acquire("b"));
        fail.set(false);
        time.set(1_000_000_000);
        assertEquals(Decision.allowed(), limiter.acquire("b"));
    }
    @Test void concurrentReclamationCannotCreateDuplicateAllowances() throws Exception {
        var time = new AtomicLong();
        var limiter = new RateLimiter(1, 1, 8, time::get);
        for (int i = 0; i < 8; i++) limiter.acquire("client" + i);
        time.set(1_000_000_000);
        var results = race(64, i -> limiter.acquire("client" + (i % 16)));
        assertEquals(8, results.stream().filter(d -> d.status() == Decision.Status.ALLOWED).count());
    }
    private static List<Decision> race(int count, java.util.function.IntFunction<Decision> action) throws Exception {
        var ready = new CountDownLatch(count);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Decision>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> { ready.countDown(); await(start); return action.apply(index); }));
            }
            try { assertTrue(ready.await(5, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            List<Decision> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(5, TimeUnit.SECONDS));
            return results;
        }
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
