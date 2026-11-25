package com.app.service;

import com.app.entity.Product;
import com.app.entity.ProductVariant;
import com.app.event.InventoryStockChangedEvent;
import com.app.request.ProductRequest;
import com.app.request.ProductVariantRequest;
import com.app.request.VariantUpdateRequest;
import com.app.response.PageResponse;
import com.app.response.ProductResponse;
import com.app.response.ProductVariantResponse;
import com.app.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.security.SecureRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {
    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);
    private static final String PRODUCT_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom PRODUCT_CODE_RANDOM = new SecureRandom();
    private static final Map<String, String> SORT_FIELDS = Map.ofEntries(
            Map.entry("name", "name"),
            Map.entry("price", "price"),
            Map.entry("offerPrice", "offerPrice"),
            Map.entry("discount", "discount"),
            Map.entry("listDate", "listDate"),
            Map.entry("stockCount", "stockCount"),
            Map.entry("productCode", "productCode"),
            Map.entry("createdAt", "createdAt"));

    private final ProductRepository products;
    private final MongoTemplate mongo;
    private final ProductInventorySync inventorySync;

    public ProductService(ProductRepository products, MongoTemplate mongo, ProductInventorySync inventorySync) {
        this.products = products;
        this.mongo = mongo;
        this.inventorySync = inventorySync;
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product(
                request.name(), request.description(), request.category(), request.productType(),
                request.baseColor(), request.price(), request.discount(), request.offerPrice(),
                request.listDate(), createVariants(request.variants()));
        product.assignProductCode(generateUniqueProductCode());
        Product saved = products.save(product);
        inventorySync.publishSnapshot(saved);
        ProductResponse response = toResponse(saved);
        logger.info("Product created: id={}, name={}, type={}, category={}",
                response.id(), response.name(), response.productType(), response.category());
        return response;
    }

    public ProductResponse update(String id, ProductRequest request) {
        Product product = find(id);
        String previousName = product.getName();
        Map<String, ProductVariant> existingVariants = product.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<ProductVariant> updatedVariants = new ArrayList<>();
        Set<UUID> requestedVariantIds = new HashSet<>();
        if (request.variants() != null) {
            for (ProductVariantRequest variantRequest : request.variants()) {
                UUID variantId = variantRequest.id();
                if (variantId == null) {
                    updatedVariants.add(new ProductVariant(
                            variantRequest.color(), variantRequest.imageUrls(), variantRequest.stockCount()));
                    continue;
                }
                if (!requestedVariantIds.add(variantId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product variant cannot be repeated");
                }
                ProductVariant existing = existingVariants.get(variantId.toString());
                if (existing == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Variant does not belong to this product: " + variantId);
                }
                updatedVariants.add(new ProductVariant(
                        variantId, variantRequest.color(), variantRequest.imageUrls(), variantRequest.stockCount()));
            }
        }
        product.update(request.name(), request.description(), request.category(), request.productType(),
                request.baseColor(), request.price(), request.discount(), request.offerPrice(),
                request.listDate(), updatedVariants);
        Product saved = products.save(product);
        publishStockChanges(previousName, saved, existingVariants);
        ProductResponse response = toResponse(saved);
        logger.info("Product updated: id={}, name={}, stockCount={}",
                response.id(), response.name(), response.stockCount());
        return response;
    }

    public void delete(String id) {
        Product product = find(id);
        product.getVariants().forEach(variant ->
                inventorySync.remove(product.getId(), product.getProductCode(), variant.getId()));
        products.delete(product);
        logger.warn("Product deleted: id={}, name={}", id, product.getName());
    }

    public ProductResponse addVariant(String id, ProductVariantRequest request) {
        Product product = find(id);
        ProductVariant addedVariant = new ProductVariant(request.color(), request.imageUrls(), request.stockCount());
        product.addVariant(addedVariant);
        Product saved = products.save(product);
        inventorySync.publish(saved, addedVariant);
        ProductResponse response = toResponse(saved);
        logger.info("Variant added: productId={}, color={}, stockCount={}",
                id, request.color(), request.stockCount());
        return response;
    }

    public ProductResponse updateVariant(String id, UUID variantId, VariantUpdateRequest request) {
        Product product = find(id);
        ProductVariant oldVariant = findVariant(product, variantId);
        int previousStock = oldVariant.getStockCount();
        String previousColor = oldVariant.getColor();
        String previousName = product.getName();
        product.updateVariant(variantId, request.color(), request.imageUrls(), request.stockCount());
        Product saved = products.save(product);
        ProductVariant savedVariant = findVariant(saved, variantId);
        if (previousStock != savedVariant.getStockCount()) inventorySync.publish(saved, savedVariant);
        else if (!previousColor.equals(savedVariant.getColor()) || !previousName.equals(saved.getName())) {
            inventorySync.publishMetadata(saved, savedVariant);
        }
        ProductResponse response = toResponse(saved);
        logger.info("Variant updated: productId={}, variantId={}, color={}, stockCount={}",
                id, variantId, request.color(), request.stockCount());
        return response;
    }

    public ProductResponse updateVariantStock(String id, UUID variantId, int stockCount) {
        Product product = find(id);
        product.updateVariantStock(variantId, stockCount);
        Product saved = products.save(product);
        ProductVariant variant = findVariant(saved, variantId);
        inventorySync.publish(saved, variant);
        ProductResponse response = toResponse(saved);
        logger.info("Variant stock updated: productId={}, variantId={}, color={}, stockCount={}, inStock={}",
                id, variantId, variant.getColor(), stockCount, variant.isInStock());
        return response;
    }

    public ProductResponse updateVariantImages(String id, UUID variantId, List<String> imageUrls) {
        Product product = find(id);
        ProductVariant variant = product.updateVariantImages(variantId, imageUrls);
        ProductResponse response = toResponse(products.save(product));
        logger.info("Variant images updated: productId={}, variantId={}, imageCount={}",
                id, variantId, imageUrls.size());
        return response;
    }

    public ProductResponse deleteVariant(String id, UUID variantId) {
        Product product = find(id);
        product.deleteVariant(variantId);
        Product saved = products.save(product);
        inventorySync.remove(saved.getId(), saved.getProductCode(), variantId.toString());
        ProductResponse response = toResponse(saved);
        logger.warn("Variant deleted: productId={}, variantId={}", id, variantId);
        return response;
    }

    private void publishStockChanges(String previousName, Product saved,
                                     Map<String, ProductVariant> previousVariants) {
        Set<String> currentVariantIds = saved.getVariants().stream()
                .map(ProductVariant::getId).collect(Collectors.toSet());
        for (ProductVariant variant : saved.getVariants()) {
            ProductVariant oldVariant = previousVariants.get(variant.getId());
            if (oldVariant == null || oldVariant.getStockCount() != variant.getStockCount()) {
                inventorySync.publish(saved, variant);
            } else if (!java.util.Objects.equals(oldVariant.getColor(), variant.getColor())
                    || !java.util.Objects.equals(previousName, saved.getName())) {
                inventorySync.publishMetadata(saved, variant);
            }
        }
        previousVariants.keySet().stream()
                .filter(variantId -> !currentVariantIds.contains(variantId))
                .forEach(variantId ->
                        inventorySync.remove(saved.getId(), saved.getProductCode(), variantId));
    }

    private ProductVariant findVariant(Product product, UUID variantId) {
        return product.getVariants().stream()
                .filter(variant -> variant.getId().equals(variantId.toString()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product variant not found"));
    }

    public ProductResponse findById(String id) {
        return toResponse(find(id));
    }

    public PageResponse<ProductResponse> search(
            String search,
            String category,
            String productType,
            List<String> colors,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Integer minStock,
            Integer maxStock,
            String stockOperator,
            Integer stockValue,
            Integer colorCount,
            BigDecimal minDiscount,
            BigDecimal maxDiscount,
            LocalDate listedFrom,
            LocalDate listedTo,
            String sortBy,
            String direction,
            int page,
            int size) {

        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be zero or greater");
        if (size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minStock != null && minStock < 0) || (maxStock != null && maxStock < 0)
                || (colorCount != null && colorCount < 0)
                || (minDiscount != null && (minDiscount.signum() < 0 || minDiscount.compareTo(BigDecimal.valueOf(100)) > 0))
                || (maxDiscount != null && (maxDiscount.signum() < 0 || maxDiscount.compareTo(BigDecimal.valueOf(100)) > 0))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filter values are outside their allowed range");
        }
        if ((minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
                || (minStock != null && maxStock != null && minStock > maxStock)
                || (minDiscount != null && maxDiscount != null && minDiscount.compareTo(maxDiscount) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum filters must not exceed maximum filters");
        }
        if ((stockOperator == null) != (stockValue == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "stockOperator and stockValue must be provided together");
        }
        if (stockValue != null && stockValue < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stockValue must be non-negative");
        }

        String sortField = SORT_FIELDS.getOrDefault(sortBy == null ? "createdAt" : sortBy, "createdAt");
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Criteria criteria = new Criteria();
        List<Criteria> conditions = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            String term = java.util.regex.Pattern.quote(search.trim());
            conditions.add(new Criteria().orOperator(
                    Criteria.where("name").regex(term, "i"),
                    Criteria.where("description").regex(term, "i"),
                    Criteria.where("productCode").regex(term, "i"),
                    Criteria.where("baseColor").regex(term, "i"),
                    Criteria.where("category").regex(term, "i"),
                    Criteria.where("productType").regex(term, "i"),
                    Criteria.where("variants.color").regex(term, "i")));
        }
        if (category != null && !category.isBlank()) conditions.add(Criteria.where("category").regex(category, "i"));
        if (productType != null && !productType.isBlank()) conditions.add(Criteria.where("productType").regex(productType, "i"));
        if (colors != null && !colors.isEmpty()) conditions.add(Criteria.where("variants.color").in(colors));
        if (colorCount != null) conditions.add(Criteria.where("variants").size(colorCount));
        if (minPrice != null) conditions.add(Criteria.where("offerPrice").gte(minPrice));
        if (maxPrice != null) conditions.add(Criteria.where("offerPrice").lte(maxPrice));
        if (inStock != null) conditions.add(Criteria.where("inStock").is(inStock));
        if (minStock != null) conditions.add(Criteria.where("stockCount").gte(minStock));
        if (maxStock != null) conditions.add(Criteria.where("stockCount").lte(maxStock));
        if (stockOperator != null) {
            Criteria stock = Criteria.where("stockCount");
            switch (stockOperator.toLowerCase(java.util.Locale.ROOT)) {
                case "eq" -> stock.is(stockValue);
                case "gte" -> stock.gte(stockValue);
                case "lte" -> stock.lte(stockValue);
                case "gt" -> stock.gt(stockValue);
                case "lt" -> stock.lt(stockValue);
                case "ne" -> stock.ne(stockValue);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "stockOperator must be one of eq, gte, lte, gt, lt, ne");
            }
            conditions.add(stock);
        }
        if (minDiscount != null) conditions.add(Criteria.where("discount").gte(minDiscount));
        if (maxDiscount != null) conditions.add(Criteria.where("discount").lte(maxDiscount));
        if (listedFrom != null) conditions.add(Criteria.where("listDate").gte(listedFrom));
        if (listedTo != null) conditions.add(Criteria.where("listDate").lte(listedTo));
        if (!conditions.isEmpty()) criteria.andOperator(conditions.toArray(Criteria[]::new));

        Query query = new Query(criteria);
        long total = mongo.count(query, Product.class);
        query.with(Sort.by(sortDirection, sortField)).skip((long) page * size).limit(size);
        List<ProductResponse> content = mongo.find(query, Product.class).stream()
                .map(this::toResponse).toList();
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages, page == 0, page + 1 >= totalPages);
    }

    private Product find(String id) {
        return products.findByProductCode(id).or(() -> products.findById(id)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    public long count() {
        return products.count();
    }

    public Map<String, Integer> syncInventory() {
        int syncedVariants = 0;
        List<CompletableFuture<?>> acknowledgements = new ArrayList<>();
        for (Product product : products.findAll()) {
            inventorySync.reconcileSnapshot(product, acknowledgements);
            syncedVariants += product.getVariants().size();
        }
        try {
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            logger.error("Catalog stock reconciliation could not be published to Kafka", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not publish catalog stock reconciliation. Try again when Kafka is available.",
                    exception.getCause());
        }
        logger.info("Catalog stock synchronization requested: variants={}", syncedVariants);
        return Map.of("syncedVariants", syncedVariants);
    }

    public Map<String, Integer> syncInventoryMetadata() {
        int syncedVariants = 0;
        List<CompletableFuture<?>> acknowledgements = new ArrayList<>();
        for (Product product : products.findAll()) {
            inventorySync.publishMetadataSnapshot(product, acknowledgements);
            syncedVariants += product.getVariants().size();
        }
        try {
            CompletableFuture.allOf(acknowledgements.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            logger.error("Catalog metadata synchronization could not be published to Kafka", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not publish catalog metadata. Try again when Kafka is available.",
                    exception.getCause());
        }
        logger.info("Catalog metadata synchronization requested: variants={}", syncedVariants);
        return Map.of("syncedVariants", syncedVariants);
    }

    public void applyInventoryStock(InventoryStockChangedEvent event) {
        Product product = products.findById(event.productId()).orElse(null);
        if (product == null) {
            logger.warn("Inventory stock update references missing product: productId={}, variantId={}",
                    event.productId(), event.variantId());
            return;
        }
        try {
            ProductVariant variant = product.updateVariantStock(UUID.fromString(event.variantId()), event.quantity());
            products.save(product);
            logger.info("Inventory stock reflected in catalog: productId={}, variantId={}, quantity={}",
                    product.getId(), variant.getId(), variant.getStockCount());
        } catch (IllegalArgumentException | ResponseStatusException exception) {
            logger.warn("Inventory stock update references missing/invalid variant: productId={}, variantId={}",
                    event.productId(), event.variantId(), exception);
        }
    }

    public void backfillProductCodes() {
        List<Product> existingProducts = products.findAll();
        Set<String> reservedCodes = existingProducts.stream()
                .map(Product::getProductCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        List<Product> productsWithoutCodes = existingProducts.stream()
                .filter(product -> product.getProductCode() == null || product.getProductCode().isBlank())
                .toList();
        for (Product product : productsWithoutCodes) {
            String code = generateUniqueProductCode(reservedCodes);
            product.assignProductCode(code);
            reservedCodes.add(code);
        }
        if (!productsWithoutCodes.isEmpty()) {
            products.saveAll(productsWithoutCodes);
            logger.info("Generated product codes for existing products: count={}", productsWithoutCodes.size());
        }
    }

    private String generateUniqueProductCode() {
        return generateUniqueProductCode(Set.of());
    }

    private String generateUniqueProductCode(Set<String> reservedCodes) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(10);
            for (int index = 0; index < 10; index++) {
                code.append(PRODUCT_CODE_CHARACTERS.charAt(
                        PRODUCT_CODE_RANDOM.nextInt(PRODUCT_CODE_CHARACTERS.length())));
            }
            if (!reservedCodes.contains(code.toString()) && !products.existsByProductCode(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique product code after 20 attempts");
    }

    private List<ProductVariant> createVariants(List<ProductVariantRequest> requests) {
        if (requests == null) requests = List.of();
        return requests.stream()
                .map(request -> new ProductVariant(
                        request.color(), request.imageUrls(), request.stockCount()))
                .toList();
    }

    private ProductResponse toResponse(Product product) {
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(variant -> new ProductVariantResponse(
                        UUID.fromString(variant.getId()), variant.getColor(), List.copyOf(variant.getImageUrls()),
                        variant.isInStock(), variant.getStockCount()))
                .toList();
        return new ProductResponse(
                product.getId(), product.getProductCode(), product.getName(), product.getDescription(),
                product.getCategory(), product.getProductType(), product.getBaseColor(),
                product.getPrice(), product.getDiscount(), product.getOfferPrice(), product.getListDate(),
                product.isInStock(), product.getStockCount(), product.getCreatedAt(), product.getUpdatedAt(),
                variants);
    }
}
