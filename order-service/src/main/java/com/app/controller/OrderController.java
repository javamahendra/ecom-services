package com.app.controller;

import com.app.entity.OrderDocument;
import com.app.request.CreateOrderRequest;
import com.app.service.OrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping
    public ResponseEntity<OrderDocument> create(@Valid @RequestBody CreateOrderRequest request,
                                                 Authentication authentication) {
        String email = null;
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().hasClaim("email")) {
            email = jwt.getToken().getClaimAsString("email");
        }
        CreateOrderRequest normalized = new CreateOrderRequest(email, request.items(), request.shippingAddress());
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.create(authentication.getName(), normalized));
    }

    @GetMapping
    public Map<String, Object> search(Authentication auth,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return orders.search(auth.getName(), isStaff(auth), status, search, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public OrderDocument get(@PathVariable String id, Authentication auth) {
        return orders.get(id, auth.getName(), isStaff(auth));
    }

    @PostMapping("/{id}/cancel")
    public OrderDocument cancel(@PathVariable String id, Authentication auth) {
        return orders.cancel(id, auth.getName(), isStaff(auth));
    }

    private boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_admin") || a.getAuthority().equals("ROLE_manager"));
    }
}
