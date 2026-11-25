package com.app.controller;

import com.app.entity.InventoryItem;
import com.app.request.InventoryRequest;
import com.app.service.InventoryService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    @GetMapping
    public Map<String, Object> search(@RequestParam(required = false) String productId,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(required = false) Integer maxQuantity,
            @RequestParam(required = false) String quantityOperator,
            @RequestParam(required = false) Integer quantityValue,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return inventory.search(productId, color, search, minQuantity, maxQuantity,
                quantityOperator, quantityValue, fromDate, toDate, page, size, sortBy, direction);
    }

    @PutMapping
    public InventoryItem upsert(@Valid @RequestBody InventoryRequest request) {
        return inventory.upsert(request);
    }

    @PostMapping("/sync-product")
    public Map<String, String> syncProduct(@RequestParam("productId") String productId,
                                           @RequestParam("variantId") String variantId) {
        inventory.syncToProduct(productId, variantId);
        return Map.of("message", "Current inventory quantity queued for product catalog synchronization.");
    }
}
