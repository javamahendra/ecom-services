package com.app.repository;

import com.app.entity.OrderDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderRepository extends MongoRepository<OrderDocument, String> {}
