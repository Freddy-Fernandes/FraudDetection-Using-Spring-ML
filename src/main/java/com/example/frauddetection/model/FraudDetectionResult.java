package com.example.frauddetection.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudDetectionResult {

    private Boolean isFraud;

    private Double fraudScore; // 0.0 to 1.0

    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL

    private String fraudStatus; // SAFE, SUSPICIOUS, FRAUD

    private String recommendation; // APPROVE, REVIEW, DECLINE

    @Builder.Default
    private List<String> triggeredRules = new ArrayList<>();

    private String primaryReason;

    @Builder.Default
    private List<String> allReasons = new ArrayList<>();

    // Scores from different components
    private Double mlScore;

    private Double ruleBasedScore;

    private Double behaviorScore;

    private Double velocityScore;

    // Behavioral flags
    private Boolean unusualAmount;

    private Boolean unusualTime;

    private Boolean unusualLocation;

    private Boolean unusualDevice;

    private Boolean highVelocity;

    private Boolean newDevice;

    private Boolean newLocation;

    // Deviation metrics
    private Double amountDeviation; // Standard deviations from mean

    private Double velocityDeviation;

    private Double locationDeviation;

    // User context
    private Double userTrustScore;

    private Integer userFraudHistory;

    // Additional metadata
    private String detectionMethod; // ML, RULE, HYBRID

    private Long processingTimeMs;

    public void addTriggeredRule(String rule) {
        if (triggeredRules == null) {
            triggeredRules = new ArrayList<>();
        }
        triggeredRules.add(rule);
    }

    public void addReason(String reason) {
        if (allReasons == null) {
            allReasons = new ArrayList<>();
        }
        allReasons.add(reason);
    }

    public String calculateRiskLevel() {
        if (fraudScore >= 0.9) {
            return "CRITICAL";
        } else if (fraudScore >= 0.7) {
            return "HIGH";
        } else if (fraudScore >= 0.4) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    public String calculateFraudStatus() {
        if (fraudScore >= 0.7) {
            return "FRAUD";
        } else if (fraudScore >= 0.4) {
            return "SUSPICIOUS";
        } else {
            return "SAFE";
        }
    }

    public String calculateRecommendation() {
        if (fraudScore >= 0.7) {
            return "DECLINE";
        } else if (fraudScore >= 0.4) {
            return "REVIEW";
        } else {
            return "APPROVE";
        }
    }
}