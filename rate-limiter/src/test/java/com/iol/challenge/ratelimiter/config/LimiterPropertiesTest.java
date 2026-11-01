package com.iol.challenge.ratelimiter.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LimiterPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LimiterProperties.class)
    static class PropertiesConfiguration { }
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("limiter.capacity=10", "limiter.refill-tokens-per-second=2", "limiter.max-clients=10000");
    @Test void bindsValidConfiguration() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(new LimiterProperties(10, 2, 10000), context.getBean(LimiterProperties.class));
        });
    }
    @Test void rejectsInvalidConfiguration() {
        for (String property : new String[]{"capacity", "refill-tokens-per-second", "max-clients"}) {
            for (String value : new String[]{"0", "-1", "1000001", "1.5", "wrong"}) {
                runner.withPropertyValues("limiter." + property + "=" + value)
                        .run(context -> assertNotNull(context.getStartupFailure()));
            }
        }
    }
}
