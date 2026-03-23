package com.example.frauddetection.service;

import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.User;
import com.example.frauddetection.entity.UserBehavior;
import com.example.frauddetection.model.FraudDetectionResult;
import com.example.frauddetection.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final UserRepository userRepository;
    private final UserBehaviorService behaviorService;
    private final MLFraudDetectionService mlService;

    /**
     * Main fraud detection method
     */
    public FraudDetectionResult detectFraud(Transaction transaction) {
        try {
            // 1. Get User
            User user = userRepository.findByUserId(transaction.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 2. Get UserBehavior
            UserBehavior behavior = behaviorService.getUserBehavior(transaction.getUserId());

            // 3. Call ML fraud detection (returns 0.0-1.0)
            double mlFraudProbability = mlService.predictFraudProbability(
                    transaction, behavior, user);

            // Convert to 0-10 scale
            double mlScore = mlFraudProbability * 10.0;

            // 4. Build result
            FraudDetectionResult result = FraudDetectionResult.builder()
                    .fraudScore(mlScore)
                    .mlScore(mlScore)
                    .detectionMethod("ML")
                    .userTrustScore(user.getTrustScore())
                    .userFraudHistory(user.getFraudCount())
                    .build();

            // 5. Calculate status
            result.setFraudStatus(result.calculateFraudStatus());
            result.setRiskLevel(result.calculateRiskLevel());
            result.setRecommendation(result.calculateRecommendation());
            result.setIsFraud(mlScore > 7.0);

            // 6. Get explanation
            String explanation = mlService.getExplanation(transaction, behavior, user);
            result.setPrimaryReason(explanation);
            result.addReason(explanation);

            log.info("Fraud detection completed for tx {}: score={}, status={}",
                    transaction.getTransactionId(), mlScore, result.getFraudStatus());

            return result;

        } catch (Exception e) {
            log.error("Fraud detection failed for tx {}: {}",
                    transaction.getTransactionId(), e.getMessage(), e);
            throw new RuntimeException("Fraud detection failed", e);
        }
    }

    /**
     * Get fraud statistics for a user
     */
    public FraudStatistics getUserFraudStatistics(String userId) {
        try {
            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            UserBehavior behavior = behaviorService.getUserBehavior(userId);

            // Calculate account age
            long accountAgeDays = ChronoUnit.DAYS.between(
                    user.getCreatedAt(),
                    LocalDateTime.now());

            // Calculate fraud rate
            int totalTx = user.getTotalTransactions() != null ? user.getTotalTransactions() : 0;
            int fraudTx = user.getFraudCount() != null ? user.getFraudCount() : 0;
            double fraudRate = totalTx > 0 ? (fraudTx * 100.0 / totalTx) : 0.0;

            // Determine risk level
            String riskLevel;
            if (user.getTrustScore() < 30)
                riskLevel = "HIGH";
            else if (user.getTrustScore() < 60)
                riskLevel = "MEDIUM";
            else
                riskLevel = "LOW";

            return FraudStatistics.builder()
                    .userId(userId)
                    .totalTransactions(totalTx)
                    .fraudulentTransactions(fraudTx)
                    .suspiciousTransactions(0)
                    .fraudRate(fraudRate)
                    .averageFraudScore(0.0)
                    .currentTrustScore(user.getTrustScore())
                    .accountAgeDays((int) accountAgeDays)
                    .riskLevel(riskLevel)
                    .lastFraudulentActivity(behavior != null ? behavior.getLastFraudulentActivity() : null)
                    .consecutiveSafeTransactions(0)
                    .build();

        } catch (Exception e) {
            log.error("Error getting fraud statistics for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to get fraud statistics", e);
        }
    }

    /**
     * Fraud Statistics DTO - Inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudStatistics {
        private String userId;
        private Integer totalTransactions;
        private Integer fraudulentTransactions;
        private Integer suspiciousTransactions;
        private Double fraudRate;
        private Double averageFraudScore;
        private Double currentTrustScore;
        private Integer accountAgeDays;
        private String riskLevel;
        private LocalDateTime lastFraudulentActivity;
        private Integer consecutiveSafeTransactions;
    }
}