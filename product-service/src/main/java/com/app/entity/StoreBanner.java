package com.app.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "store_settings")
public class StoreBanner {
    @Id
    private String id = "home-banner";
    private boolean enabled;
    private String eyebrow;
    private String heading;
    private String accentText;
    private String description;
    private String imageUrl;
    private String imageAlt;
    private String buttonText;
    private Instant updatedAt;

    protected StoreBanner() {}

    public StoreBanner(boolean enabled, String eyebrow, String heading, String accentText,
                       String description, String imageUrl, String imageAlt, String buttonText) {
        this.id = "home-banner";
        this.update(enabled, eyebrow, heading, accentText, description, imageUrl, imageAlt, buttonText);
    }

    public void update(boolean enabled, String eyebrow, String heading, String accentText,
                       String description, String imageUrl, String imageAlt, String buttonText) {
        this.enabled = enabled;
        this.eyebrow = eyebrow;
        this.heading = heading;
        this.accentText = accentText;
        this.description = description;
        this.imageUrl = imageUrl;
        this.imageAlt = imageAlt;
        this.buttonText = buttonText;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getEyebrow() { return eyebrow; }
    public String getHeading() { return heading; }
    public String getAccentText() { return accentText; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getImageAlt() { return imageAlt; }
    public String getButtonText() { return buttonText; }
    public Instant getUpdatedAt() { return updatedAt; }
}
