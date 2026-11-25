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
            Map<String, Object> productSearch = (Map<String, Object>) paths.get("/api/products").get("get");
            List<Map<String, Object>> productParameters = (List<Map<String, Object>>) productSearch.get("parameters");
            assertTrue(productParameters.stream().anyMatch(parameter -> "colorCount".equals(parameter.get("name"))),
                    "Product search must document the colorCount filter");
            assertTrue(paths.containsKey("/api/products/count"));
            assertTrue(paths.containsKey("/api/products/inventory/sync"));
            assertTrue(paths.get("/api/products/inventory/sync").containsKey("post"));
            assertTrue(paths.containsKey("/api/products/inventory/metadata-sync"));
            assertTrue(paths.get("/api/products/inventory/metadata-sync").containsKey("post"));
            assertTrue(paths.containsKey("/api/users/count"));
            assertTrue(paths.containsKey("/api/orders/count"));
            assertTrue(paths.containsKey("/api/orders/dispatches/sync"));
            assertTrue(paths.get("/api/orders/dispatches/sync").containsKey("post"));
            for (String countPath : List.of(
                    "/api/products/count", "/api/users/count", "/api/orders/count", "/api/dispatches/count")) {
                assertTrue(paths.get(countPath).containsKey("get"), countPath + " must document GET");
            }
            assertTrue(paths.containsKey("/api/cart/items"));
            assertTrue(paths.get("/api/inventory").containsKey("get"));
            assertTrue(paths.containsKey("/api/inventory/sync-product"));
            assertTrue(paths.get("/api/inventory/sync-product").containsKey("post"));
            assertTrue(paths.containsKey("/api/wishlist/items/{productId}/{variantId}"));
            assertTrue(paths.containsKey("/api/notifications/delivery/run"));
            List<Map<String, Object>> servers = (List<Map<String, Object>>) document.get("servers");
            assertTrue(servers.size() >= 4);
            assertTrue(((Map<String, Object>) document.get("info")).containsKey("x-logo"));
        }
    }
}
