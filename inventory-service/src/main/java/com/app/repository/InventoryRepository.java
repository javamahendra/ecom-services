package com.app.repository;

import com.app.entity.InventoryItem;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {}
