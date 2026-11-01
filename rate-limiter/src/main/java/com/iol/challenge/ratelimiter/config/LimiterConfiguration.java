package com.iol.challenge.ratelimiter.config;

import com.iol.challenge.ratelimiter.core.RateLimiter;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LimiterConfiguration {
    @Bean
    RateLimiter rateLimiter(LimiterProperties properties) {
        LoggerFactory.getLogger(LimiterConfiguration.class).info(
                "Limiter capacity={}, refillTokensPerSecond={}, maxClients={}",
                properties.capacity(), properties.refillTokensPerSecond(), properties.maxClients());
        return new RateLimiter(properties.capacity(), properties.refillTokensPerSecond(),
                properties.maxClients(), System::nanoTime);
    }
}
