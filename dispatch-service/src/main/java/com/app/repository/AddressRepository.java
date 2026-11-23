package com.app.repository;

import com.app.entity.AddressDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AddressRepository extends MongoRepository<AddressDocument, String> {
    List<AddressDocument> findByUserId(String userId);
}
