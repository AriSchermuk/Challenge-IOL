package com.iol.challenge.ratelimiter.core;

final class TokenBucket {
    private static final long TOKEN = 1_000_000_000L;
    private final long maximum;
    private final int rate;
    private long credit;
    private long last;
    private boolean initialized;

    TokenBucket(int capacity, int rate) {
        this.maximum = capacity * TOKEN;
        this.credit = maximum;
        this.rate = rate;
    }

    Decision acquire(long now) {
        refill(now);
        if (credit >= TOKEN) {
            credit -= TOKEN;
            return Decision.allowed();
        }
        return Decision.limited(ceilDivide(TOKEN - credit, rate));
    }

    boolean full(long now) {
        refill(now);
        return credit == maximum;
    }

    private void refill(long now) {
        if (!initialized) {
            initialized = true;
            last = now;
        }
        long elapsed = now - last;
        if (elapsed < 0) return;
        // Compare before multiplying: even a very long idle period cannot overflow.
        long untilFull = ceilDivide(maximum - credit, rate);
        credit = elapsed >= untilFull ? maximum : credit + elapsed * rate;
        last = now;
    }

    private static long ceilDivide(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}
