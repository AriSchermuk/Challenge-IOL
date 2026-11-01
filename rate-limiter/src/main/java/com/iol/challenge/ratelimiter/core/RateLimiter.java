package com.iol.challenge.ratelimiter.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public final class RateLimiter {
    private final int capacity;
    private final int rate;
    private final int maxClients;
    private final LongSupplier clock;
    private final Map<String, Entry> entries = new HashMap<>();
    private final ReentrantLock registry = new ReentrantLock();

    public RateLimiter(int capacity, int rate, int maxClients, LongSupplier clock) {
        validate(capacity);
        validate(rate);
        validate(maxClients);
        this.capacity = capacity;
        this.rate = rate;
        this.maxClients = maxClients;
        this.clock = Objects.requireNonNull(clock);
    }

    public Decision acquire(String clientId) {
        Objects.requireNonNull(clientId);
        Entry entry = pin(clientId);
        if (entry == null) return Decision.saturated();
        try {
            entry.lock.lock();
            try {
                return entry.bucket.acquire(clock.getAsLong());
            } finally {
                entry.lock.unlock();
            }
        } finally {
            registry.lock();
            try {
                entry.references--;
            } finally {
                registry.unlock();
            }
        }
    }

    private Entry pin(String clientId) {
        registry.lock();
        try {
            Entry entry = entries.get(clientId);
            if (entry == null) {
                if (entries.size() == maxClients && !reclaim()) return null;
                entry = new Entry(new TokenBucket(capacity, rate));
                entries.put(clientId, entry);
            }
            // Includes callers waiting for this bucket's lock, preventing stale references.
            entry.references++;
            return entry;
        } finally {
            registry.unlock();
        }
    }

    private boolean reclaim() {
        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.references != 0) continue;
            // No request can pin the entry while registry is held. Never wait here.
            if (!entry.lock.tryLock()) continue;
            try {
                if (entry.bucket.full(clock.getAsLong())) {
                    iterator.remove();
                    return true;
                }
            } finally {
                entry.lock.unlock();
            }
        }
        return false;
    }

    private static void validate(int value) {
        if (value < 1 || value > 1_000_000) {
            throw new IllegalArgumentException("Policy values must be between 1 and 1000000");
        }
    }

    private static final class Entry {
        private final ReentrantLock lock = new ReentrantLock();
        private final TokenBucket bucket;
        private long references;
        private Entry(TokenBucket bucket) { this.bucket = bucket; }
    }
}
