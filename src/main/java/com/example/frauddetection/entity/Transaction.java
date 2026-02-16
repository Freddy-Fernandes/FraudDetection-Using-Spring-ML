package com.example.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_transaction_time", columnList = "transactionTime"),
        @Index(name = "idx_fraud_status", columnList = "fraudStatus")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Double amount;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private String transactionType; // QR_CODE, UPI, CARD, WALLET

    @Column(nullable = false)
    private LocalDateTime transactionTime;

    private String merchantId;

    private String merchantName;

    private String merchantCategory;

    // Location data
    private String ipAddress;

    private String country;

    private String city;

    private Double latitude;

    private Double longitude;

    // Device information
    private String deviceId;

    private String deviceType; // MOBILE, WEB, TABLET

    private String deviceFingerprint;

    private String userAgent;

    // QR Code specific
    private String qrCodeId;

    private String qrCodeData;

    // Transaction status
    @Column(nullable = false)
    private String status; // PENDING, APPROVED, DECLINED, PROCESSING

    @Column(nullable = false)
    private String fraudStatus; // SAFE, SUSPICIOUS, FRAUD

    private Double fraudScore; // 0.0 to 1.0

    private String fraudReason;

    // Behavioral features
    private Long timeSinceLastTransaction; // in seconds

    private Integer transactionsInLastHour;

    private Integer transactionsInLastDay;

    private Double avgTransactionAmount;

    private Boolean unusualTime; // Transaction at unusual hour

    private Boolean unusualAmount; //Transaction at unusual amount

    private Boolean unusualLocation; // Transaction from new location

    private Boolean unusualDevice; // Transaction from new device

    private Double velocityScore; // Transaction frequency score

    // Additional metadata
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        transactionTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
