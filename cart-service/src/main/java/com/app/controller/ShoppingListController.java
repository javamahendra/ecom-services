package com.app.controller;

import com.app.request.AddCartItemRequest;
import com.app.request.AddWishlistItemRequest;
import com.app.request.UpdateCartQuantityRequest;
import com.app.response.ShoppingListResponse;
import com.app.service.ShoppingListService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShoppingListController {
    private final ShoppingListService shoppingLists;

    public ShoppingListController(ShoppingListService shoppingLists) {
        this.shoppingLists = shoppingLists;
    }

    @GetMapping("/api/cart")
    public ShoppingListResponse getCart(Authentication authentication) {
        return shoppingLists.getCart(authentication.getName());
    }

    @PostMapping("/api/cart/items")
    public ShoppingListResponse addToCart(@Valid @RequestBody AddCartItemRequest request,
                                          Authentication authentication) {
        return shoppingLists.addToCart(authentication.getName(), request);
    }

    @PutMapping("/api/cart/items/{productId}/{variantId}")
    public ShoppingListResponse updateQuantity(@PathVariable String productId,
                                               @PathVariable String variantId,
                                               @Valid @RequestBody UpdateCartQuantityRequest request,
                                               Authentication authentication) {
        return shoppingLists.updateCartQuantity(authentication.getName(), productId, variantId, request.quantity());
    }

    @DeleteMapping("/api/cart/items/{productId}/{variantId}")
    public ShoppingListResponse removeFromCart(@PathVariable String productId,
                                               @PathVariable String variantId,
                                               Authentication authentication) {
        return shoppingLists.removeFromCart(authentication.getName(), productId, variantId);
    }

    @DeleteMapping("/api/cart/items")
    public ShoppingListResponse clearCart(Authentication authentication) {
        return shoppingLists.clearCart(authentication.getName());
    }

    @GetMapping("/api/wishlist")
    public ShoppingListResponse getWishlist(Authentication authentication) {
        return shoppingLists.getWishlist(authentication.getName());
    }

    @PostMapping("/api/wishlist/items")
    public ShoppingListResponse addToWishlist(@Valid @RequestBody AddWishlistItemRequest request,
                                              Authentication authentication) {
        return shoppingLists.addToWishlist(authentication.getName(), request);
    }

    @DeleteMapping("/api/wishlist/items/{productId}/{variantId}")
    public ShoppingListResponse removeFromWishlist(@PathVariable String productId,
                                                   @PathVariable String variantId,
                                                   Authentication authentication) {
        return shoppingLists.removeFromWishlist(authentication.getName(), productId, variantId);
    }

    @PostMapping("/api/wishlist/items/{productId}/{variantId}/move-to-cart")
    public ShoppingListResponse moveToCart(@PathVariable String productId,
                                           @PathVariable String variantId,
                                           @Valid @RequestBody UpdateCartQuantityRequest request,
                                           Authentication authentication) {
        return shoppingLists.moveToCart(authentication.getName(), productId, variantId, request.quantity());
    }
}
