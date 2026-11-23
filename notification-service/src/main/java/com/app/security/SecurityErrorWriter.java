package com.app.security;

import com.app.config.RequestLoggingFilter;
import com.app.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

final class SecurityErrorWriter {
    private SecurityErrorWriter() {}

    static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        Object value = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(Instant.now(), status,
                HttpStatus.valueOf(status).getReasonPhrase(), code, message,
                request.getRequestURI(), requestId, Map.of()));
    }
}
