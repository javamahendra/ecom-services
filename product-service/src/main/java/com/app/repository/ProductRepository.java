package com.app.repository;

import com.app.entity.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
    boolean existsByName(String name);
    boolean existsByProductCode(String productCode);
    Optional<Product> findByProductCode(String productCode);
    List<Product> findAllByProductCodeIn(Collection<String> productCodes);
}
