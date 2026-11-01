package com.iol.challenge.ratelimiter.core;

final class TokenBucket {
    private static final long CREDIT_UNITS_PER_TOKEN = 1_000_000_000L;
    private final long maximumCredit;
    private final int refillTokensPerSecond;
    private long availableCredit;
    private long lastAccountingNanos;
    private boolean initialized;

    TokenBucket(int capacityTokens, int refillTokensPerSecond) {
        this.maximumCredit = capacityTokens * CREDIT_UNITS_PER_TOKEN;
        this.availableCredit = maximumCredit;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    Decision acquire(long nowNanos) {
        refill(nowNanos);
        if (availableCredit >= CREDIT_UNITS_PER_TOKEN) {
            availableCredit -= CREDIT_UNITS_PER_TOKEN;
            return Decision.allowed();
        }
        return Decision.limited(ceilDivide(CREDIT_UNITS_PER_TOKEN - availableCredit, refillTokensPerSecond));
    }

    boolean full(long nowNanos) {
        refill(nowNanos);
        return availableCredit == maximumCredit;
    }

    private void refill(long nowNanos) {
        if (!initialized) {
            initialized = true;
            lastAccountingNanos = nowNanos;
        }
        long elapsedNanos = nowNanos - lastAccountingNanos;
        if (elapsedNanos < 0) return;
        // Compare before multiplying: even a very long idle period cannot overflow.
        long nanosUntilFull = ceilDivide(maximumCredit - availableCredit, refillTokensPerSecond);
        availableCredit = elapsedNanos >= nanosUntilFull
                ? maximumCredit
                : availableCredit + elapsedNanos * refillTokensPerSecond;
        lastAccountingNanos = nowNanos;
    }

    private static long ceilDivide(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
