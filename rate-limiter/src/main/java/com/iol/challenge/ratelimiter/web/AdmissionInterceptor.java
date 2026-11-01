package com.iol.challenge.ratelimiter.web;

import com.iol.challenge.ratelimiter.core.Decision;
import com.iol.challenge.ratelimiter.core.RateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public final class AdmissionInterceptor implements HandlerInterceptor {
    private final RateLimiter limiter;
    public AdmissionInterceptor(RateLimiter limiter) { this.limiter = limiter; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (request.getDispatcherType() != DispatcherType.REQUEST || !(handler instanceof HandlerMethod)) {
            return true;
        }
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, OPTIONS");
            response.setStatus("OPTIONS".equals(request.getMethod()) ? 200 : 405);
            return false;
        }
        Optional<String> clientId = readClientId(request);
        if (clientId.isEmpty()) return writeError(response, 400, "invalid_client_id");
        Decision decision = limiter.acquire(clientId.orElseThrow());
        return writeDecision(response, decision);
    }

    private static Optional<String> readClientId(HttpServletRequest request) {
        var clientIdHeaders = request.getHeaders("X-Client-Id");
        if (!clientIdHeaders.hasMoreElements()) return Optional.empty();
        String clientId = clientIdHeaders.nextElement();
        if (clientIdHeaders.hasMoreElements() || !clientId.matches("[A-Za-z0-9_-]{1,64}")) {
            return Optional.empty();
        }
        return Optional.of(clientId);
    }

    private static boolean writeDecision(HttpServletResponse response, Decision decision) throws IOException {
        return switch (decision.status()) {
            case ALLOWED -> true;
            case LIMITED -> {
                response.setHeader("Retry-After", Long.toString(
                        (decision.retryAfterNanos() + 999_999_999L) / 1_000_000_000L));
                yield writeError(response, 429, "rate_limit_exceeded");
            }
            case SATURATED -> writeError(response, 503, "client_capacity_exceeded");
        };
    }

    private static boolean writeError(HttpServletResponse response, int status, String errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + errorCode + "\"}");
        return false;
    }
}
