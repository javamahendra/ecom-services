package com.app.repository;

import com.app.entity.PendingInventoryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingInventoryStatusRepository extends MongoRepository<PendingInventoryStatus, String> {}
