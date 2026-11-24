package com.app.repository;

import com.app.entity.StockImportAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StockImportAuditRepository extends MongoRepository<StockImportAudit, String> {
    Page<StockImportAudit> findAllByOrderByUploadedAtDesc(Pageable pageable);
}
