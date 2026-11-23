package com.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductCatalogClient {
    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);
    private final RestClient restClient;

    public ProductCatalogClient(@Value("${app.product-service.url:http://localhost:8091}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ProductSnapshot getItem(String productId, String variantId) {
        try {
            Product product = restClient.get().uri("/api/products/{id}", productId).retrieve()
                    .body(Product.class);
            if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            Variant variant = product.variants() == null ? null : product.variants().stream()
                    .filter(item -> item.id() != null && item.id().toString().equals(variantId))
                    .findFirst().orElse(null);
            if (variant == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product color variant not found");
            return new ProductSnapshot(product.id(), product.name(), product.category(), product.productType(),
                    variant.id().toString(), variant.color(), product.offerPrice() == null ? product.price() : product.offerPrice(),
                    variant.imageUrls() == null ? List.of() : variant.imageUrls(),
                    variant.inStock(), variant.stockCount());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "The product or selected color variant was not found.", exception);
            }
            log.error("Product catalog returned an error: productId={}, status={}",
                    productId, exception.getStatusCode().value());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Product information is temporarily unavailable. Please try again.", exception);
        } catch (RestClientException exception) {
            log.error("Product catalog lookup failed: productId={}, variantId={}", productId, variantId, exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Product information is temporarily unavailable. Please try again.", exception);
        }
    }

    private record Product(String id, String name, String category, String productType, BigDecimal price,
                           BigDecimal offerPrice, List<Variant> variants) {}
    private record Variant(UUID id, String color, List<String> imageUrls, boolean inStock, int stockCount) {}
    public record ProductSnapshot(String productId, String productName, String category, String productType,
                                  String variantId, String color, BigDecimal unitPrice, List<String> imageUrls,
                                  boolean inStock, int availableStock) {}
}
