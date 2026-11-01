package com.iol.challenge.ratelimiter.web;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = DemoController.class)
public class ApplicationErrors {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        LoggerFactory.getLogger(ApplicationErrors.class).error("Unexpected application failure", exception);
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
                .body(Map.of("error", "internal_error"));
    }
}
