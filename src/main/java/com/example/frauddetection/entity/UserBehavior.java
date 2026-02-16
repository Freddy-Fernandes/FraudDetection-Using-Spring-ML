package com.example.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_behavior")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    // Transaction patterns
    private Double avgTransactionAmount;

    private Double maxTransactionAmount;

    private Double minTransactionAmount;

    private Double stdDevTransactionAmount;

    private Integer transactionsPerDay;

    private Integer transactionsPerWeek;

    private Integer transactionsPerMonth;

    // Time patterns
    @Column(columnDefinition = "TEXT")
    private String preferredTransactionHours; // JSON array of hours

    @Column(columnDefinition = "TEXT")
    private String preferredTransactionDays; // JSON array of days

    // Location patterns
    @Column(columnDefinition = "TEXT")
    private String frequentLocations; // JSON array of locations

    @Column(columnDefinition = "TEXT")
    private String frequentCountries; // JSON array of countries

    // Device patterns
    @Column(columnDefinition = "TEXT")
    private String knownDevices; // JSON array of device fingerprints

    @Column(columnDefinition = "TEXT")
    private String knownIpAddresses; // JSON array of IPs

    // Merchant patterns
    @Column(columnDefinition = "TEXT")
    private String frequentMerchants; // JSON array of merchant IDs

    @Column(columnDefinition = "TEXT")
    private String frequentCategories; // JSON array of categories

    // Behavioral scores
    private Double consistencyScore; // How consistent is user behavior

    private Double diversityScore; // How diverse are transactions

    private Double velocityPattern; // Normal transaction frequency

    // Risk indicators
    private Integer failedAttempts;

    private Integer chargebacks;

    private Integer disputedTransactions;

    private LocalDateTime lastFraudulentActivity;

    // Update tracking
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    private Integer dataPointsCount; // Number of transactions analyzed

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}