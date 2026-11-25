package com.app.controller;

import com.app.request.ProductRequest;
import com.app.request.StockUpdateRequest;
import com.app.request.VariantImagesRequest;
import com.app.request.VariantUpdateRequest;
import com.app.request.ProductVariantRequest;
import com.app.response.PageResponse;
import com.app.response.ProductResponse;
import com.app.response.PageResponse;
import com.app.response.StockImportAuditResponse;
import com.app.response.StockImportResponse;
import com.app.service.ProductService;
import com.app.service.StockImportActor;
import com.app.service.ProductStockImportService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;
    private final ProductStockImportService stockImports;

    public ProductController(ProductService products, ProductStockImportService stockImports) {
        this.products = products;
        this.stockImports = stockImports;
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
            @RequestParam(required = false) Integer maxStock,
            @RequestParam(required = false) String stockOperator,
            @RequestParam(required = false) Integer stockValue,
            @RequestParam(required = false) Integer colorCount,
            @RequestParam(required = false) BigDecimal minDiscount,
            @RequestParam(required = false) BigDecimal maxDiscount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate listedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate listedTo,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String direction,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return products.search(search, category, productType, colors, minPrice, maxPrice, inStock,
                minStock, maxStock, stockOperator, stockValue, colorCount, minDiscount, maxDiscount,
                listedFrom, listedTo, sortBy, direction, page, size);
    }

    @GetMapping("/count")
    public long count() {
        return products.count();
    }

    @PostMapping("/inventory/sync")
    public java.util.Map<String, Integer> syncInventory() {
        return products.syncInventory();
    }

    @PostMapping("/inventory/metadata-sync")
    public java.util.Map<String, Integer> syncInventoryMetadata() {
        return products.syncInventoryMetadata();
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable String id) {
        return products.findById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse product = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + product.productCode())).body(product);
    }

    @PostMapping(value = "/stock-import", consumes = "multipart/form-data")
    public StockImportResponse importStock(@RequestParam("file") MultipartFile file,
                                           Authentication authentication) {
        JwtAuthenticationToken jwtAuthentication = (JwtAuthenticationToken) authentication;
        var jwt = jwtAuthentication.getToken();
        String displayName = jwt.getClaimAsString("preferred_username");
        if (displayName == null || displayName.isBlank()) {
            displayName = jwt.getClaimAsString("email");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = jwt.getSubject();
        }
        return stockImports.importStock(file, new StockImportActor(jwt.getSubject(), displayName));
    }

    @GetMapping("/stock-imports")
    public PageResponse<StockImportAuditResponse> stockImportHistory(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        return stockImports.findImportHistory(page, size);
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
