package com.app.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.app.config.RequestLoggingFilter;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class GatewayControllerTests {
    private HttpServer upstream;

    @BeforeEach
    void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/api/products/123", exchange -> {
            if (!"Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))
                    || !"color=black".equals(exchange.getRequestURI().getRawQuery())) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            byte[] body = "{\"id\":\"123\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.createContext("/api/orders", exchange -> {
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        upstream.start();
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop(0);
    }

    @Test
    void routesAllApplicationApisToTheirOwningServices() {
        assertEquals("auth", GatewayController.route("/api/auth/login"));
        assertEquals("auth", GatewayController.route("/api/users"));
        assertEquals("product", GatewayController.route("/api/banner"));
        assertEquals("product", GatewayController.route("/api/products/123"));
        assertEquals("order", GatewayController.route("/api/orders/123"));
        assertEquals("inventory", GatewayController.route("/api/inventory"));
        assertEquals("notification", GatewayController.route("/api/notifications/delivery/days"));
        assertEquals("dispatch", GatewayController.route("/api/addresses"));
        assertEquals("dispatch", GatewayController.route("/api/addresses/address-123"));
        assertEquals("dispatch", GatewayController.route("/api/dispatches/123"));
        assertEquals("cart", GatewayController.route("/api/cart/items"));
        assertEquals("cart", GatewayController.route("/api/wishlist/items"));
    }

    @Test
    void routesIdentityProviderEndpointsAndRejectsUnknownPaths() {
        assertEquals("keycloak", GatewayController.route("/realms/auth_service/protocol/openid-connect/token"));
        assertEquals("keycloak", GatewayController.route("/resources/app.css"));
        assertNull(GatewayController.route("/api/unknown"));
    }

    @Test
    void forwardsPathQueryAuthorizationAndUpstreamResponse() throws Exception {
        String baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
        ObjectMapper objectMapper = JsonMapper.builder().build();
        GatewayController gateway = new GatewayController(baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, baseUrl, RestClient.builder(), objectMapper,
                new SimpleMeterRegistry());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/products/123");
        request.setQueryString("color=black");
        request.addHeader("Authorization", "Bearer test-token");
        request.setAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gateway.proxy(request, response);

        assertEquals(201, response.getStatus());
        assertEquals("{\"id\":\"123\"}", response.getContentAsString());
        assertEquals("request-123", response.getHeader("X-Request-Id"));
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void recordsBusinessRequestByOperationAndStatus() throws Exception {
        String baseUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        GatewayController gateway = new GatewayController(baseUrl, baseUrl, baseUrl, baseUrl,
                baseUrl, baseUrl, baseUrl, baseUrl, RestClient.builder(),
                JsonMapper.builder().build(), meterRegistry);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        gateway.proxy(request, response);

        assertEquals(201, response.getStatus());
        assertEquals(1.0, meterRegistry.counter("ecommerce.business.requests",
                "operation", "order_create", "status", "201").count());
    }
}
