package com.app.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object realmAccessClaim = jwt.getClaims().get("realm_access");
            if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)
                    || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (Object role : roles) {
                if (role instanceof String roleName) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                }
            }
            return authorities;
        });

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/banner").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/banner").hasRole("admin")
                .requestMatchers(HttpMethod.GET, "/api/products/stock-imports").hasAnyRole("admin", "manager")
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products/stock-import").hasAnyRole("admin", "manager")
                .requestMatchers(HttpMethod.POST, "/api/products").hasAnyRole("admin", "manager")
                .requestMatchers(HttpMethod.POST, "/api/products/*/variants").hasRole("admin")
                .requestMatchers(HttpMethod.PATCH, "/api/products/*/variants/*/stock").hasAnyRole("admin", "manager")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("admin")
                .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("admin")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("admin")
                .requestMatchers("/actuator/**").hasRole("admin")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint((request, response, exception) -> {
                    String reason = exception.getMessage() == null ? "" : exception.getMessage();
                    if (reason.toLowerCase().contains("expired")) {
                        logger.warn("Security log: expired access token: method={}, uri={}, remote={}",
                                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                    } else {
                        logger.warn("Security log: invalid access token: method={}, uri={}, remote={}",
                                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                    }
                    SecurityErrorWriter.write(objectMapper, request, response, 401, "UNAUTHORIZED",
                            "Your access token is missing, invalid, or expired. Sign in again.");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    logger.warn("Security log: admin role required: method={}, uri={}, remote={}",
                            request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                    SecurityErrorWriter.write(objectMapper, request, response, 403, "ACCESS_DENIED",
                            "You do not have permission to access this resource.");
                }));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8090}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
