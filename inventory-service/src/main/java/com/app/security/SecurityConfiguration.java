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
    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaims().get("realm_access");
            if (!(claim instanceof Map<?, ?> realm) || !(realm.get("roles") instanceof Collection<?> roles)) return List.of();
            List<GrantedAuthority> result = new ArrayList<>();
            for (Object role : roles) if (role instanceof String name) result.add(new SimpleGrantedAuthority("ROLE_" + name));
            return result;
        });
        http.cors(Customizer.withDefaults()).csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/api/inventory/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/inventory/**").hasRole("admin")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/sync-product").hasRole("admin")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(r -> r.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((req, res, ex) -> {
                            log.warn("Security: invalid/expired token method={} uri={} remote={}",
                                    req.getMethod(), req.getRequestURI(), req.getRemoteAddr());
                            SecurityErrorWriter.write(objectMapper, req, res, 401, "UNAUTHORIZED",
                                    "Your access token is missing, invalid, or expired. Sign in again.");
                        }).accessDeniedHandler((req, res, ex) -> {
                            log.warn("Security: inventory admin role required uri={} remote={}", req.getRequestURI(), req.getRemoteAddr());
                            SecurityErrorWriter.write(objectMapper, req, res, 403, "ACCESS_DENIED",
                                    "You do not have permission to access this resource.");
                        }));
        return http.build();
    }
    @Bean
    CorsConfigurationSource cors(@Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8090}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(java.util.Arrays.stream(origins.split(",")).map(String::trim).toList()); c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type")); c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource(); s.registerCorsConfiguration("/api/**", c); return s;
    }
}
