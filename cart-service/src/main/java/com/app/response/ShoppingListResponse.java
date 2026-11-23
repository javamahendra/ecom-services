package com.app.response;

import com.app.entity.ShoppingItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShoppingListResponse(String userId, List<ShoppingItem> items, int itemCount,
                                   BigDecimal subtotal, Instant updatedAt) {}
