package com.iol.challenge.ratelimiter.web;

import static org.junit.jupiter.api.Assertions.*;
import com.iol.challenge.ratelimiter.core.RateLimiter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdmissionInterceptorTest {
    @Test void errorDispatchAndNonControllerHandlersDoNotConsumeAllowance() throws Exception {
        var limiter = new RateLimiter(1, 1, 1, () -> 0);
        var interceptor = new AdmissionInterceptor(limiter);
        var request = new MockHttpServletRequest("GET", "/api/demo");
        request.setDispatcherType(DispatcherType.ERROR);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        request.setDispatcherType(DispatcherType.REQUEST);
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(com.iol.challenge.ratelimiter.core.Decision.allowed(), limiter.acquire("a"));
    }
}
