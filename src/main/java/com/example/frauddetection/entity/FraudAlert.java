package com.example.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String alertType; // RULE_BASED, ML_BASED, HYBRID

    @Column(nullable = false)
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(nullable = false)
    private Double fraudScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String rulesFired; // JSON array of rules that triggered

    @Column(columnDefinition = "TEXT")
    private String mlFeatures; // JSON object of ML features

    private String action; // BLOCK, REVIEW, ALLOW_WITH_WARNING

    private Boolean reviewed = false;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewComments;

    private Boolean confirmedFraud;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        detectedAt = LocalDateTime.now();
    }
}