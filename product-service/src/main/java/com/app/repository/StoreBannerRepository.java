package com.app.repository;

import com.app.entity.StoreBanner;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StoreBannerRepository extends MongoRepository<StoreBanner, String> {}
