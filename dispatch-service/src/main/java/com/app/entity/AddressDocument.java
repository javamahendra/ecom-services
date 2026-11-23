package com.app.entity;

import com.app.request.AddressRequest;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("addresses")
public class AddressDocument {
    @Id private String id;
    private String userId;
    private String label;
    private String recipientName;
    private String phone;
    private String line1;
    private String line2;
    private String city;
    private String region;
    private String postalCode;
    private String country;
    private boolean defaultAddress;
    private Instant createdAt;
    private Instant updatedAt;
    public AddressDocument() {}
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void populate(String userId, AddressRequest request) {
        populate(userId, request, request.defaultAddress());
    }
    public void populate(String userId, AddressRequest request, boolean defaultAddress) {
        this.userId = userId; this.label = request.label(); this.recipientName = request.recipientName();
        this.phone = request.phone(); this.line1 = request.line1(); this.line2 = request.line2();
        this.city = request.city(); this.region = request.region(); this.postalCode = request.postalCode();
        this.country = request.country(); this.defaultAddress = defaultAddress;
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
        this.updatedAt = Instant.now();
    }
}
