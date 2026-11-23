package com.app.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

class OpenApiDocumentTests {
    @Test
    @SuppressWarnings("unchecked")
    void consolidatedOpenApiYamlParsesAndListsApplicationOperations() throws Exception {
        try (InputStream input = new ClassPathResource("static/openapi.yaml").getInputStream()) {
            Map<String, Object> document = new Yaml().load(input);
            assertTrue(document.containsKey("openapi"));
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) document.get("paths");
            assertTrue(paths.containsKey("/api/products"));
            assertTrue(paths.containsKey("/api/cart/items"));
            assertTrue(paths.containsKey("/api/wishlist/items/{productId}/{variantId}"));
            assertTrue(paths.containsKey("/api/notifications/delivery/run"));
            List<Map<String, Object>> servers = (List<Map<String, Object>>) document.get("servers");
            assertTrue(servers.size() >= 4);
            assertTrue(((Map<String, Object>) document.get("info")).containsKey("x-logo"));
        }
    }
}
