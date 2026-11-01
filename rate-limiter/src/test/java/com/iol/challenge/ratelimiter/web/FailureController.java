package com.iol.challenge.ratelimiter.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Test-only operation verifies advice mapping and admission charging on endpoint failure.
@RestController
public class FailureController {
    @GetMapping("/api/failure")
    public void fail() { throw new IllegalStateException("Private failure details"); }
}
