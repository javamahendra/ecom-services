package com.app.controller;

import com.app.request.ProductRequest;
import com.app.request.StockUpdateRequest;
import com.app.request.VariantImagesRequest;
import com.app.request.VariantUpdateRequest;
import com.app.request.ProductVariantRequest;
import com.app.response.PageResponse;
import com.app.response.ProductResponse;
import com.app.service.ProductService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) List<String> colors,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Integer minStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate listedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate listedTo,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String direction,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return products.search(search, category, productType, colors, minPrice, maxPrice, inStock, minStock,
                listedFrom, listedTo, sortBy, direction, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable String id) {
        return products.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        products.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/variants")
    public ProductResponse addVariant(@PathVariable String id,
                                      @Valid @RequestBody ProductVariantRequest request) {
        return products.addVariant(id, request);
    }

    @PutMapping("/{id}/variants/{variantId}")
    public ProductResponse updateVariant(@PathVariable String id,
                                         @PathVariable UUID variantId,
                                         @Valid @RequestBody VariantUpdateRequest request) {
        return products.updateVariant(id, variantId, request);
    }

    @PatchMapping("/{id}/variants/{variantId}/stock")
    public ProductResponse updateVariantStock(@PathVariable String id,
                                               @PathVariable UUID variantId,
                                               @Valid @RequestBody StockUpdateRequest request) {
        return products.updateVariantStock(id, variantId, request.stockCount());
    }

    @PatchMapping("/{id}/variants/{variantId}/images")
    public ProductResponse updateVariantImages(@PathVariable String id,
                                                @PathVariable UUID variantId,
                                                @Valid @RequestBody VariantImagesRequest request) {
        return products.updateVariantImages(id, variantId, request.imageUrls());
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public ProductResponse deleteVariant(@PathVariable String id,
                                         @PathVariable UUID variantId) {
        return products.deleteVariant(id, variantId);
    }
}
