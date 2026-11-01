package com.iol.challenge.ratelimiter.web;

import com.iol.challenge.ratelimiter.core.RateLimiter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        if (!request.getMethod().equals("GET")) {
            response.setHeader("Allow", "GET, OPTIONS");
            response.setStatus(request.getMethod().equals("OPTIONS") ? 200 : 405);
            return false;
        }
        var headers = request.getHeaders("X-Client-Id");
        if (!headers.hasMoreElements()) return error(response, 400, "invalid_client_id");
        String id = headers.nextElement();
        if (headers.hasMoreElements() || !id.matches("[A-Za-z0-9_-]{1,64}")) {
            return error(response, 400, "invalid_client_id");
        }
        var decision = limiter.acquire(id);
        return switch (decision.status()) {
            case ALLOWED -> true;
            case LIMITED -> {
                response.setHeader("Retry-After", Long.toString(
                        (decision.retryAfterNanos() + 999_999_999L) / 1_000_000_000L));
                yield error(response, 429, "rate_limit_exceeded");
            }
            case SATURATED -> error(response, 503, "client_capacity_exceeded");
        };
    }

    private static boolean error(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
        return false;
    }
}
