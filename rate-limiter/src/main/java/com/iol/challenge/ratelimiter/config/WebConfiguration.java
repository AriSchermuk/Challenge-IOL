package com.iol.challenge.ratelimiter.config;

import com.iol.challenge.ratelimiter.core.RateLimiter;
import com.iol.challenge.ratelimiter.web.AdmissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final RateLimiter limiter;
    public WebConfiguration(RateLimiter limiter) { this.limiter = limiter; }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdmissionInterceptor(limiter)).addPathPatterns("/api/**");
    }
}
