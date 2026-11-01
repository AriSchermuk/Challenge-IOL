package com.iol.challenge.ratelimiter.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

public final class RateLimiter {
    private final int capacityTokens;
    private final int refillTokensPerSecond;
    private final int maxClients;
    private final LongSupplier nanoTimeSource;
    private final Map<String, ClientEntry> clientEntries = new HashMap<>();
    private final ReentrantLock registryLock = new ReentrantLock();

    public RateLimiter(int capacityTokens, int refillTokensPerSecond, int maxClients, LongSupplier nanoTimeSource) {
        validate(capacityTokens);
        validate(refillTokensPerSecond);
        validate(maxClients);
        this.capacityTokens = capacityTokens;
        this.refillTokensPerSecond = refillTokensPerSecond;
        this.maxClients = maxClients;
        this.nanoTimeSource = Objects.requireNonNull(nanoTimeSource);
    }

    public Decision acquire(String clientId) {
        Objects.requireNonNull(clientId);
        Optional<ClientEntry> pinnedEntry = pin(clientId);
        if (pinnedEntry.isEmpty()) return Decision.saturated();
        ClientEntry entry = pinnedEntry.orElseThrow();
        try {
            entry.bucketLock.lock();
            try {
                return entry.tokenBucket.acquire(nanoTimeSource.getAsLong());
            } finally {
                entry.bucketLock.unlock();
            }
        } finally {
            unpin(entry);
        }
    }

    private void unpin(ClientEntry entry) {
        registryLock.lock();
        try {
            entry.activeOrWaitingRequests--;
        } finally {
            registryLock.unlock();
        }
    }

    private Optional<ClientEntry> pin(String clientId) {
        registryLock.lock();
        try {
            ClientEntry entry = clientEntries.get(clientId);
            if (entry == null) {
                if (clientEntries.size() == maxClients && !reclaim()) return Optional.empty();
                entry = new ClientEntry(new TokenBucket(capacityTokens, refillTokensPerSecond));
                clientEntries.put(clientId, entry);
            }
            // Includes callers waiting for this bucket's lock, preventing use of a detached bucket.
            entry.activeOrWaitingRequests++;
            return Optional.of(entry);
        } finally {
            registryLock.unlock();
        }
    }

    private boolean reclaim() {
        var iterator = clientEntries.values().iterator();
        while (iterator.hasNext()) {
            ClientEntry entry = iterator.next();
            if (entry.activeOrWaitingRequests != 0) continue;
            // No request can pin the entry while the registry lock is held. Never wait here.
            if (!entry.bucketLock.tryLock()) continue;
            try {
                if (entry.tokenBucket.full(nanoTimeSource.getAsLong())) {
                    iterator.remove();
                    return true;
                }
            } finally {
                entry.bucketLock.unlock();
            }
        }
        return false;
    }

    private static void validate(int value) {
        if (value < 1 || value > 1_000_000) {
            throw new IllegalArgumentException("Policy values must be between 1 and 1000000");
        }
    }

    private static final class ClientEntry {
        private final ReentrantLock bucketLock = new ReentrantLock();
        private final TokenBucket tokenBucket;
        private long activeOrWaitingRequests;
        private ClientEntry(TokenBucket tokenBucket) { this.tokenBucket = tokenBucket; }
    }
}
