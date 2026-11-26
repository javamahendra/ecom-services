package com.app.controller;

import com.app.config.RequestLoggingFilter;
import com.app.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Controller
public class GatewayController {
    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");
    private static final Set<String> KEYCLOAK_PATHS = Set.of("/robots.txt");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> destinations;
    private final MeterRegistry meterRegistry;

    public GatewayController(
            @Value("${gateway.routes.auth}") String auth,
            @Value("${gateway.routes.product}") String product,
            @Value("${gateway.routes.order}") String order,
            @Value("${gateway.routes.inventory}") String inventory,
            @Value("${gateway.routes.notification}") String notification,
            @Value("${gateway.routes.dispatch}") String dispatch,
            @Value("${gateway.routes.cart}") String cart,
            @Value("${gateway.routes.keycloak}") String keycloak,
            Builder restClientBuilder,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.destinations = Map.of("auth", stripTrailingSlash(auth),
                "product", stripTrailingSlash(product), "order", stripTrailingSlash(order),
                "inventory", stripTrailingSlash(inventory), "notification", stripTrailingSlash(notification),
                "dispatch", stripTrailingSlash(dispatch), "cart", stripTrailingSlash(cart),
                "keycloak", stripTrailingSlash(keycloak));
    }

    @RequestMapping({"/api/**", "/realms/**", "/resources/**", "/admin/**",
            "/health/**", "/robots.txt"})
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String service = route(path);
        if (service == null) {
            writeError(request, response, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                    "No service is configured for this endpoint.");
            return;
        }
        try {
            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            URI target = URI.create(destinations.get(service) + path
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
            byte[] body = request.getInputStream().readAllBytes();
            restClient.method(method).uri(target)
                    .headers(headers -> copyRequestHeaders(request, headers))
                    .body(body)
                    .exchange((outbound, upstream) -> {
                        response.setStatus(upstream.getStatusCode().value());
                        upstream.getHeaders().forEach((name, values) -> {
                            if (!isHopByHop(name) && !isCorsHeader(name)) {
                                for (String value : values) response.addHeader(name, value);
                            }
                        });
                        response.setHeader("X-Request-Id", requestId(request));
                        upstream.getBody().transferTo(response.getOutputStream());
                        return null;
                    });
            recordBusinessRequest(request, response.getStatus());
        } catch (IllegalArgumentException exception) {
            writeError(request, response, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                    "This HTTP method is not supported.");
            recordBusinessRequest(request, response.getStatus());
        } catch (RestClientException exception) {
            log.error("Upstream service unavailable: service={}, path={}, requestId={}",
                    service, path, requestId(request), exception);
            writeError(request, response, HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                    "The requested service is temporarily unavailable. Please try again later.");
            recordBusinessRequest(request, response.getStatus());
        }
    }

    private void recordBusinessRequest(HttpServletRequest request, int status) {
        String operation = businessOperation(request);
        if (operation != null) {
            meterRegistry.counter("ecommerce.business.requests",
                    "operation", operation, "status", Integer.toString(status)).increment();
        }
    }

    private static String businessOperation(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        return switch (request.getRequestURI()) {
            case "/api/orders" -> "order_create";
            case "/api/users" -> "user_registration";
            case "/api/users/admin" -> "admin_user_create";
            case "/api/auth/login" -> "login";
            default -> null;
        };
    }

    static String route(String path) {
        if (path.startsWith("/api/auth/") || path.equals("/api/users") || path.startsWith("/api/users/")) return "auth";
        if (path.equals("/api/banner")) return "product";
        if (path.equals("/api/products") || path.startsWith("/api/products/")) return "product";
        if (path.equals("/api/orders") || path.startsWith("/api/orders/")) return "order";
        if (path.equals("/api/inventory") || path.startsWith("/api/inventory/")) return "inventory";
        if (path.equals("/api/notifications") || path.startsWith("/api/notifications/")) return "notification";
        if (path.equals("/api/addresses") || path.startsWith("/api/addresses/")) return "dispatch";
        if (path.equals("/api/dispatches") || path.startsWith("/api/dispatches/")) return "dispatch";
        if (path.equals("/api/cart") || path.startsWith("/api/cart/")) return "cart";
        if (path.equals("/api/wishlist") || path.startsWith("/api/wishlist/")) return "cart";
        if (path.startsWith("/realms/") || path.startsWith("/resources/")
                || path.startsWith("/admin/") || path.startsWith("/health/")
                || KEYCLOAK_PATHS.contains(path)) return "keycloak";
        return null;
    }

    private static void copyRequestHeaders(HttpServletRequest request,
                                          org.springframework.http.HttpHeaders target) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (isHopByHop(name) || "x-request-id".equalsIgnoreCase(name)
                    || "x-forwarded-host".equalsIgnoreCase(name)
                    || "x-forwarded-proto".equalsIgnoreCase(name)
                    || "x-forwarded-for".equalsIgnoreCase(name)) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) target.add(name, values.nextElement());
        }
        target.set("X-Request-Id", requestId(request));
        target.set("X-Forwarded-For", request.getRemoteAddr());
        String host = request.getHeader("Host");
        target.set("X-Forwarded-Host", host == null ? request.getServerName() : host);
        target.set("X-Forwarded-Proto", request.getScheme());
    }

    private static boolean isHopByHop(String name) {
        return HOP_BY_HOP_HEADERS.contains(name.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isCorsHeader(String name) {
        return name.regionMatches(true, 0, "Access-Control-", 0, "Access-Control-".length());
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
        return value instanceof String id ? id : UUID.randomUUID().toString();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatusCode status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(),
                HttpStatus.valueOf(status.value()).getReasonPhrase(), code, message,
                request.getRequestURI(), requestId(request), Map.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
