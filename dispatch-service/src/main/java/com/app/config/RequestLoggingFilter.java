package com.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    public static final String REQUEST_ID_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader("X-Request-Id", requestId);
        MDC.put("requestId", requestId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException exception) {
            log.error("HTTP request failed: method={}, path={}, requestId={}",
                    request.getMethod(), request.getRequestURI(), requestId, exception);
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("HTTP request completed: method={}, path={}, status={}, durationMs={}, requestId={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs, requestId);
            } else if (status >= 400) {
                log.warn("HTTP request completed: method={}, path={}, status={}, durationMs={}, requestId={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs, requestId);
            } else {
                log.info("HTTP request completed: method={}, path={}, status={}, durationMs={}, requestId={}",
                        request.getMethod(), request.getRequestURI(), status, durationMs, requestId);
            }
            MDC.remove("requestId");
        }
    }
}
