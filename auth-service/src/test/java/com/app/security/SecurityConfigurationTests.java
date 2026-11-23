package com.app.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigurationTests {
    @Test
    void allowsConfiguredLocalFrontendOrigins() {
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration cors = new SecurityConfiguration()
                .corsConfigurationSource("http://localhost:3000,http://localhost:5173,http://localhost:8090")
                .getCorsConfiguration(request);

        assertEquals("http://localhost:5173", cors.checkOrigin("http://localhost:5173"));
        assertEquals("http://localhost:8090", cors.checkOrigin("http://localhost:8090"));
        assertNull(cors.checkOrigin("http://localhost:4200"));
    }

    @Test
    void allowsAnyOriginWhenWildcardPatternIsConfigured() {
        HttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        CorsConfiguration cors = new SecurityConfiguration()
                .corsConfigurationSource("*")
                .getCorsConfiguration(request);

        assertEquals("https://any-origin.example", cors.checkOrigin("https://any-origin.example"));
    }
}
