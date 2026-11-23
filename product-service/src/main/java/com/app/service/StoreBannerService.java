package com.app.service;

import com.app.entity.StoreBanner;
import com.app.repository.StoreBannerRepository;
import com.app.request.StoreBannerRequest;
import org.springframework.stereotype.Service;

@Service
public class StoreBannerService {
    private static final String DEFAULT_EYEBROW = "A more thoughtful everyday";
    private static final String DEFAULT_HEADING = "Good things,";
    private static final String DEFAULT_ACCENT = "well chosen.";
    private static final String DEFAULT_DESCRIPTION =
            "Useful, beautiful essentials made with intention. A little less, a lot better.";
    private static final String DEFAULT_IMAGE =
            "https://images.unsplash.com/photo-1490312278390-ab64016e0aa9?auto=format&fit=crop&w=1500&q=90";
    private static final String DEFAULT_ALT = "A calm, light-filled home with considered everyday objects";
    private static final String DEFAULT_BUTTON = "Explore the collection";

    private final StoreBannerRepository banners;

    public StoreBannerService(StoreBannerRepository banners) {
        this.banners = banners;
    }

    public StoreBanner get() {
        return banners.findById("home-banner").orElseGet(() -> new StoreBanner(
                true, DEFAULT_EYEBROW, DEFAULT_HEADING, DEFAULT_ACCENT, DEFAULT_DESCRIPTION,
                DEFAULT_IMAGE, DEFAULT_ALT, DEFAULT_BUTTON));
    }

    public StoreBanner update(StoreBannerRequest request) {
        StoreBanner banner = banners.findById("home-banner").orElseGet(() -> new StoreBanner(
                request.enabled(), request.eyebrow(), request.heading(), request.accentText(),
                request.description(), request.imageUrl(), request.imageAlt(), request.buttonText()));
        banner.update(request.enabled(), request.eyebrow(), request.heading(), request.accentText(),
                request.description(), request.imageUrl(), request.imageAlt(), request.buttonText());
        return banners.save(banner);
    }
}
