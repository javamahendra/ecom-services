package com.app.repository;

import com.app.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
    boolean existsByName(String name);
}
