package com.app.repository;

import com.app.entity.ProcessedOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcessedOrderRepository extends MongoRepository<ProcessedOrder, String> {}
