package com.app.service;

import com.app.repository.ProductRepository;
import com.app.request.ProductRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProductSampleSeeder implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(ProductSampleSeeder.class);
    private static final String SAMPLE_PRODUCTS_RESOURCE = "sample-products.json";

    private final ObjectMapper objectMapper;
    private final ProductRepository products;
    private final ProductService productService;

    public ProductSampleSeeder(
            ObjectMapper objectMapper, ProductRepository products, ProductService productService) {
        this.objectMapper = objectMapper;
        this.products = products;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<ProductRequest> samples;
        try (var input = new ClassPathResource(SAMPLE_PRODUCTS_RESOURCE).getInputStream()) {
            samples = objectMapper.readValue(input, new TypeReference<>() {});
        }
        int created = 0;
        int skipped = 0;
        for (ProductRequest sample : samples) {
            if (products.existsByName(sample.name())) {
                skipped++;
                continue;
            }
            productService.create(sample);
            created++;
        }
        logger.info("Sample product initialization complete: created={}, skipped={}, configured={}",
                created, skipped, samples.size());
    }
}
