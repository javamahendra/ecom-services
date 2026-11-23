package com.app.controller;

import com.app.entity.InventoryItem;
import com.app.request.InventoryRequest;
import com.app.service.InventoryService;
import jakarta.validation.Valid;
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
            @RequestParam(required = false) Integer minQuantity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return inventory.search(productId, color, minQuantity, page, size, sortBy, direction);
    }

    @PutMapping
    public InventoryItem upsert(@Valid @RequestBody InventoryRequest request) {
        return inventory.upsert(request);
    }
}
