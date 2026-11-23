package com.app.controller;

import com.app.entity.StoreBanner;
import com.app.request.StoreBannerRequest;
import com.app.service.StoreBannerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banner")
public class StoreBannerController {
    private final StoreBannerService banners;

    public StoreBannerController(StoreBannerService banners) {
        this.banners = banners;
    }

    @GetMapping
    public StoreBanner get() {
        return banners.get();
    }

    @PutMapping
    public StoreBanner update(@Valid @RequestBody StoreBannerRequest request) {
        return banners.update(request);
    }
}
