package com.iol.challenge.ratelimiter.core;

public record Decision(Status status, long retryAfterNanos) {
    public enum Status { ALLOWED, LIMITED, SATURATED }
    public static Decision allowed() { return new Decision(Status.ALLOWED, 0); }
    public static Decision limited(long nanos) { return new Decision(Status.LIMITED, nanos); }
    public static Decision saturated() { return new Decision(Status.SATURATED, 0); }
}
