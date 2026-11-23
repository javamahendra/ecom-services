package com.app.repository;

import com.app.entity.ShoppingList;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShoppingListRepository extends MongoRepository<ShoppingList, String> {
    Optional<ShoppingList> findByUserId(String userId);
}
