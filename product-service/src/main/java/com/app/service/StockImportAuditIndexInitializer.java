package com.app.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
class StockImportAuditIndexInitializer implements ApplicationRunner {
    private final MongoTemplate mongo;

    StockImportAuditIndexInitializer(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        mongo.indexOps("stock_import_audits")
                .ensureIndex(new Index().on("uploadedAt", Sort.Direction.DESC));
        mongo.indexOps("stock_import_audits")
                .ensureIndex(new Index().on("uploadedById", Sort.Direction.ASC));
    }
}
