package com.example.frauddetection.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private String transactionId;
    private String userId;
    private Double amount;
    private String currency;
    private String transactionType;
    private String status;
    private String fraudStatus;
    private Double fraudScore;
    private String fraudReason;
    private Boolean approved;
    private String message;
    private LocalDateTime transactionTime;
    private FraudAnalysis fraudAnalysis;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudAnalysis {
        private Double mlScore;
        private Double ruleBasedScore;
        private String riskLevel;
        private String[] triggeredRules;
        private String recommendation;
        private BehaviorAnalysis behaviorAnalysis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorAnalysis {
        private Boolean unusualAmount;
        private Boolean unusualTime;
        private Boolean unusualLocation;
        private Boolean unusualDevice;
        private Boolean highVelocity;
        private Double deviationFromNormal;
    }
}