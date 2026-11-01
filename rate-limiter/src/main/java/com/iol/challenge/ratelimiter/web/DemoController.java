package com.iol.challenge.ratelimiter.web;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    @GetMapping("/api/demo")
    public ResponseEntity<Map<String, String>> demo() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "allowed"));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
