package com.iol.challenge.ratelimiter;

import static org.junit.jupiter.api.Assertions.*;
import com.iol.challenge.ratelimiter.core.RateLimiter;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApplicationIT.ClockConfiguration.class)
class ApplicationIT {
    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired AtomicLong time;
    @TestConfiguration static class ClockConfiguration {
        @Bean AtomicLong time() { return new AtomicLong(); }
        @Bean @Primary RateLimiter testLimiter(AtomicLong time) { return new RateLimiter(1, 2, 2, () -> {
            if (time.get() < 0) throw new IllegalStateException("Private clock failure");
            return time.get();
        }); }
    }
    @Test void fullHttpContract() throws Exception {
        time.set(0);
        assertResponse(request("GET", "/health"), 200, "{\"status\":\"ok\"}");
        assertError(request("GET", "/api/demo"), 400, "invalid_client_id");
        for (String bad : new String[]{"has space", "a,b", "x".repeat(65), "bad!"}) {
            assertError(request("GET", "/api/demo", bad), 400, "invalid_client_id");
        }
        assertError(request("GET", "/api/demo", "a", "b"), 400, "invalid_client_id");
        assertEquals(404, request("GET", "/api/absent", "Alice").statusCode());
        assertEquals(405, request("POST", "/api/demo", "Alice").statusCode());
        assertEquals(405, request("HEAD", "/api/demo", "Alice").statusCode());
        var options = request("OPTIONS", "/api/demo");
        assertEquals(200, options.statusCode());
        assertEquals("GET, OPTIONS", options.headers().firstValue("Allow").orElseThrow());
        var allowed = request("GET", "/api/demo", "Alice");
        assertResponse(allowed, 200, "{\"message\":\"allowed\"}");
        assertEquals("no-store", allowed.headers().firstValue("Cache-Control").orElseThrow());
        var denied = request("GET", "/api/demo", "Alice");
        assertError(denied, 429, "rate_limit_exceeded");
        assertEquals("1", denied.headers().firstValue("Retry-After").orElseThrow());
        assertResponse(request("GET", "/health"), 200, "{\"status\":\"ok\"}");
        assertEquals(200, request("GET", "/api/demo", "alice").statusCode());
        assertError(request("GET", "/api/demo", "new"), 503, "client_capacity_exceeded");
        time.set(499_999_999);
        assertError(request("GET", "/api/demo", "Alice"), 429, "rate_limit_exceeded");
        time.set(500_000_000);
        assertEquals(200, request("GET", "/api/demo", "Alice").statusCode());
        assertEquals(200, request("GET", "/api/demo", "new").statusCode());
        time.set(1_000_000_000);
        assertError(request("GET", "/api/failure", "failure"), 500, "internal_error");
        assertError(request("GET", "/api/demo", "failure"), 429, "rate_limit_exceeded");
        time.set(-1);
        assertError(request("GET", "/api/demo", "failure"), 500, "internal_error");
        time.set(1_500_000_000);
        assertEquals(200, request("GET", "/api/demo", "failure").statusCode());
        time.set(2_000_000_000);
        assertEquals(200, request("GET", "/api/demo", "a".repeat(64)).statusCode());
    }
    private HttpResponse<String> request(String method, String path, String... ids) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(java.time.Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody());
            for (String id : ids) builder.header("x-client-id", id);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private void assertError(HttpResponse<String> response, int status, String code) {
        assertResponse(response, status, "{\"error\":\"" + code + "\"}");
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
    }
    private void assertResponse(HttpResponse<String> response, int status, String body) {
        assertEquals(status, response.statusCode());
        assertEquals(body, response.body());
    }
}
