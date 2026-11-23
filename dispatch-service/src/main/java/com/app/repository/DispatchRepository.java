package com.app.repository;

import com.app.entity.DispatchRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DispatchRepository extends MongoRepository<DispatchRecord, String> {}
