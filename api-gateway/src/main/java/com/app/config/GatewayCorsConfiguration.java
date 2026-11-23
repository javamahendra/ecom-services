package com.app.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class GatewayCorsConfiguration implements WebMvcConfigurer {
    private final List<String> allowedOrigins;

    public GatewayCorsConfiguration(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8090}")
            String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-XSRF-TOKEN")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
