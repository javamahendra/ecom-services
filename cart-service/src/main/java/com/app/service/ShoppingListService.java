package com.app.service;

import com.app.entity.ShoppingItem;
import com.app.entity.ShoppingList;
import com.app.request.AddCartItemRequest;
import com.app.request.AddWishlistItemRequest;
import com.app.response.ShoppingListResponse;
import com.app.repository.ShoppingListRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShoppingListService {
    private static final Logger log = LoggerFactory.getLogger(ShoppingListService.class);
    private final ShoppingListRepository lists;
    private final ProductCatalogClient catalog;

    public ShoppingListService(ShoppingListRepository lists, ProductCatalogClient catalog) {
        this.lists = lists;
        this.catalog = catalog;
    }

    public ShoppingListResponse getCart(String userId) {
        ShoppingList list = getOrCreate(userId);
        return cartResponse(list);
    }

    public ShoppingListResponse getWishlist(String userId) {
        ShoppingList list = getOrCreate(userId);
        return wishlistResponse(list);
    }

    public ShoppingListResponse addToCart(String userId, AddCartItemRequest request) {
        ShoppingList list = getOrCreate(userId);
        ProductCatalogClient.ProductSnapshot product = catalog.getItem(request.productId(), request.variantId());
        ShoppingItem existing = find(list.getCartItems(), request.productId(), request.variantId());
        int quantity = request.quantity() + (existing == null ? 0 : existing.getQuantity());
        validateStock(product, quantity);
        if (existing == null) {
            list.getCartItems().add(toItem(product, quantity));
        } else {
            refreshSnapshot(existing, product);
            existing.setQuantity(quantity);
        }
        list.touch();
        ShoppingList saved = lists.save(list);
        log.info("Cart item added: userId={}, productId={}, variantId={}, quantity={}",
                userId, request.productId(), request.variantId(), quantity);
        return cartResponse(saved);
    }

    public ShoppingListResponse updateCartQuantity(String userId, String productId, String variantId, int quantity) {
        ShoppingList list = getOrCreate(userId);
        ShoppingItem item = requireItem(list.getCartItems(), productId, variantId, "Cart item not found");
        ProductCatalogClient.ProductSnapshot product = catalog.getItem(productId, variantId);
        validateStock(product, quantity);
        refreshSnapshot(item, product);
        item.setQuantity(quantity);
        list.touch();
        return cartResponse(lists.save(list));
    }

    public ShoppingListResponse removeFromCart(String userId, String productId, String variantId) {
        ShoppingList list = getOrCreate(userId);
        boolean removed = list.getCartItems().removeIf(item ->
                item.getProductId().equals(productId) && item.getVariantId().equals(variantId));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        list.touch();
        return cartResponse(lists.save(list));
    }

    public ShoppingListResponse clearCart(String userId) {
        ShoppingList list = getOrCreate(userId);
        list.setCartItems(new ArrayList<>());
        return cartResponse(lists.save(list));
    }

    public ShoppingListResponse addToWishlist(String userId, AddWishlistItemRequest request) {
        ShoppingList list = getOrCreate(userId);
        if (find(list.getWishlistItems(), request.productId(), request.variantId()) != null) {
            return wishlistResponse(list);
        }
        ProductCatalogClient.ProductSnapshot product = catalog.getItem(request.productId(), request.variantId());
        list.getWishlistItems().add(toItem(product, 1));
        list.touch();
        log.info("Wishlist item added: userId={}, productId={}, variantId={}",
                userId, request.productId(), request.variantId());
        return wishlistResponse(lists.save(list));
    }

    public ShoppingListResponse removeFromWishlist(String userId, String productId, String variantId) {
        ShoppingList list = getOrCreate(userId);
        boolean removed = list.getWishlistItems().removeIf(item ->
                item.getProductId().equals(productId) && item.getVariantId().equals(variantId));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wishlist item not found");
        list.touch();
        return wishlistResponse(lists.save(list));
    }

    public ShoppingListResponse moveToCart(String userId, String productId, String variantId, int quantity) {
        ShoppingList list = getOrCreate(userId);
        requireItem(list.getWishlistItems(), productId, variantId, "Wishlist item not found");
        ProductCatalogClient.ProductSnapshot product = catalog.getItem(productId, variantId);
        ShoppingItem existing = find(list.getCartItems(), productId, variantId);
        int totalQuantity = quantity + (existing == null ? 0 : existing.getQuantity());
        validateStock(product, totalQuantity);
        if (existing == null) list.getCartItems().add(toItem(product, quantity));
        else {
            refreshSnapshot(existing, product);
            existing.setQuantity(totalQuantity);
        }
        list.getWishlistItems().removeIf(item ->
                item.getProductId().equals(productId) && item.getVariantId().equals(variantId));
        list.touch();
        return cartResponse(lists.save(list));
    }

    private ShoppingList getOrCreate(String userId) {
        return lists.findByUserId(userId).orElseGet(() -> new ShoppingList(userId));
    }

    private static void validateStock(ProductCatalogClient.ProductSnapshot product, int quantity) {
        if (!product.inStock() || product.availableStock() < quantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The requested quantity is not available. Available stock: " + product.availableStock());
        }
    }

    private static ShoppingItem toItem(ProductCatalogClient.ProductSnapshot product, int quantity) {
        return new ShoppingItem(product.productId(), product.variantId(), product.productName(),
                product.category(), product.productType(), product.color(), product.unitPrice(),
                product.imageUrls(), product.inStock(), product.availableStock(), quantity);
    }

    private static void refreshSnapshot(ShoppingItem item, ProductCatalogClient.ProductSnapshot product) {
        item.setSnapshot(product.productName(), product.category(), product.productType(), product.color(),
                product.unitPrice(), product.imageUrls(), product.inStock(), product.availableStock());
    }

    private static ShoppingItem find(List<ShoppingItem> items, String productId, String variantId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)
                && item.getVariantId().equals(variantId)).findFirst().orElse(null);
    }

    private static ShoppingItem requireItem(List<ShoppingItem> items, String productId, String variantId,
                                            String message) {
        ShoppingItem item = find(items, productId, variantId);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        return item;
    }

    private static ShoppingListResponse cartResponse(ShoppingList list) {
        List<ShoppingItem> items = List.copyOf(list.getCartItems());
        BigDecimal subtotal = items.stream().map(item ->
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ShoppingListResponse(list.getUserId(), items,
                items.stream().mapToInt(ShoppingItem::getQuantity).sum(), subtotal, list.getUpdatedAt());
    }

    private static ShoppingListResponse wishlistResponse(ShoppingList list) {
        List<ShoppingItem> items = List.copyOf(list.getWishlistItems());
        return new ShoppingListResponse(list.getUserId(), items, items.size(), BigDecimal.ZERO, list.getUpdatedAt());
    }
}
