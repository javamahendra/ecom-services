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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
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
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/api/auth/**", "/api/users/**"))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                        "/api/auth/forgot-password").permitAll()
                .requestMatchers("/api/auth/password-reset").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/users/admin").hasRole("admin")
                .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/count").hasRole("admin")
                .requestMatchers(HttpMethod.GET, "/api/users").hasRole("admin")
                .requestMatchers(HttpMethod.PUT, "/api/users/*/role").hasRole("admin")
                .requestMatchers(HttpMethod.PUT, "/api/users/*/status").hasRole("admin")
                .requestMatchers(HttpMethod.PUT, "/api/users/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("admin")
                .requestMatchers(HttpMethod.GET, "/api/users/*").authenticated()
                .requestMatchers("/api/users/**").denyAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("admin")
                .anyRequest().permitAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
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
                    logger.warn("Security log: access denied: method={}, uri={}, remote={}",
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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
