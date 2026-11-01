package com.iol.challenge.ratelimiter.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("limiter")
public record LimiterProperties(
        @Min(1) @Max(1_000_000) int capacity,
        @Min(1) @Max(1_000_000) int refillTokensPerSecond,
        @Min(1) @Max(1_000_000) int maxClients) {
}
