package com.example.frauddetection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.frauddetection.entity.FraudAlert;
import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.User;
import com.example.frauddetection.entity.UserBehavior;
import com.example.frauddetection.model.FraudDetectionResult;
import com.example.frauddetection.repository.FraudAlertRepository;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final MLFraudDetectionService mlService;
    private final RuleBasedFraudDetectionService ruleService;
    private final UserBehaviorService behaviorService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Main fraud detection method - combines ML and rule-based approaches
     */
    @Transactional
    public FraudDetectionResult detectFraud(Transaction transaction) {
        long startTime = System.currentTimeMillis();

        try {
            // Get user and behavior data
            User user = userRepository.findByUserId(transaction.getUserId()).orElse(null);
            UserBehavior behavior = behaviorService.getUserBehavior(transaction.getUserId());

            // Apply rule-based detection
            FraudDetectionResult ruleResult = ruleService.applyRules(transaction, user, behavior);

            // Apply ML-based detection
            double mlScore = mlService.predictFraudProbability(transaction, behavior);

            // Combine scores using weighted average
            // 60% ML, 40% Rules (can be adjusted based on model performance)
            double combinedScore = (mlScore * 0.6) + (ruleResult.getRuleBasedScore() * 0.4);

            // Build final result
            FraudDetectionResult result = FraudDetectionResult.builder()
                    .fraudScore(combinedScore)
                    .mlScore(mlScore)
                    .ruleBasedScore(ruleResult.getRuleBasedScore())
                    .behaviorScore(calculateBehaviorScore(behavior))
                    .isFraud(combinedScore >= 0.7)
                    .triggeredRules(ruleResult.getTriggeredRules())
                    .allReasons(ruleResult.getAllReasons())
                    .unusualAmount(ruleResult.getUnusualAmount())
                    .unusualTime(ruleResult.getUnusualTime())
                    .unusualLocation(ruleResult.getUnusualLocation())
                    .unusualDevice(ruleResult.getUnusualDevice())
                    .highVelocity(ruleResult.getHighVelocity())
                    .newDevice(ruleResult.getNewDevice())
                    .userTrustScore(user != null ? user.getTrustScore() : 100.0)
                    .userFraudHistory(user != null ? user.getFraudCount() : 0)
                    .detectionMethod("HYBRID")
                    .build();

            // Calculate derived fields
            result.setRiskLevel(result.calculateRiskLevel());
            result.setFraudStatus(result.calculateFraudStatus());
            result.setRecommendation(result.calculateRecommendation());

            // Set primary reason
            if (!result.getAllReasons().isEmpty()) {
                result.setPrimaryReason(result.getAllReasons().get(0));
            } else if (mlScore >= 0.7) {
                result.setPrimaryReason("ML model detected suspicious patterns");
                result.addReason("ML model detected suspicious patterns");
            } else {
                result.setPrimaryReason("Transaction appears normal");
            }

            // Calculate deviations
            if (behavior != null) {
                double amountDeviation = ruleService.calculateBehaviorDeviation(transaction, behavior);
                result.setAmountDeviation(amountDeviation);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(processingTime);

            log.info("Fraud detection completed for transaction {}: Score={}, Status={}, Time={}ms",
                    transaction.getTransactionId(), combinedScore, result.getFraudStatus(), processingTime);

            // Update transaction with fraud detection results
            updateTransactionWithResults(transaction, result);

            // Create fraud alert if necessary
            if (result.getFraudScore() >= 0.4) {
                createFraudAlert(transaction, result);
            }

            // Update user trust score
            if (user != null) {
                updateUserTrustScore(user, result);
            }

            // Asynchronously update user behavior (non-blocking)
            updateBehaviorAsync(transaction.getUserId());

            return result;

        } catch (Exception e) {
            log.error("Error in fraud detection for transaction: {}", transaction.getTransactionId(), e);

            // Return safe default result on error
            return FraudDetectionResult.builder()
                    .fraudScore(0.5)
                    .isFraud(false)
                    .riskLevel("MEDIUM")
                    .fraudStatus("UNKNOWN")
                    .recommendation("REVIEW")
                    .primaryReason("Error in fraud detection - manual review required")
                    .detectionMethod("ERROR")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Pre-transaction fraud check (before payment/QR scan)
     */
    public FraudDetectionResult preTransactionCheck(Transaction transaction) {
        log.info("Running pre-transaction fraud check for user: {}", transaction.getUserId());

        // Set transaction to pending
        transaction.setStatus("PENDING");

        // Run fraud detection
        FraudDetectionResult result = detectFraud(transaction);

        // Determine if transaction should proceed
        if (result.getFraudScore() >= 0.7) {
            transaction.setStatus("DECLINED");
            transaction.setFraudStatus("FRAUD");
            log.warn("Transaction declined - high fraud score: {}", result.getFraudScore());
        } else if (result.getFraudScore() >= 0.4) {
            transaction.setStatus("REVIEW");
            transaction.setFraudStatus("SUSPICIOUS");
            log.info("Transaction flagged for review - moderate fraud score: {}", result.getFraudScore());
        } else {
            transaction.setStatus("APPROVED");
            transaction.setFraudStatus("SAFE");
        }

        return result;
    }

    /**
     * Post-transaction fraud check (after QR scan/payment)
     */
    public FraudDetectionResult postTransactionCheck(Transaction transaction) {
        log.info("Running post-transaction fraud check for transaction: {}", transaction.getTransactionId());

        FraudDetectionResult result = detectFraud(transaction);

        // Take action based on fraud score
        if (result.getFraudScore() >= 0.9) {
            // Critical fraud - immediate action
            transaction.setStatus("BLOCKED");
            transaction.setFraudStatus("FRAUD");
            blockUserAccount(transaction.getUserId());
            log.error("CRITICAL FRAUD DETECTED - User account blocked: {}", transaction.getUserId());
        } else if (result.getFraudScore() >= 0.7) {
            // High fraud - hold transaction
            transaction.setStatus("HOLD");
            transaction.setFraudStatus("FRAUD");
            log.warn("High fraud score - transaction on hold: {}", result.getFraudScore());
        } else if (result.getFraudScore() >= 0.4) {
            transaction.setFraudStatus("SUSPICIOUS");
        } else {
            transaction.setFraudStatus("SAFE");
        }

        return result;
    }

    /**
     * Update transaction with fraud detection results
     */
    private void updateTransactionWithResults(Transaction transaction, FraudDetectionResult result) {
        transaction.setFraudScore(result.getFraudScore());
        transaction.setFraudStatus(result.getFraudStatus());
        transaction.setFraudReason(result.getPrimaryReason());
        transaction.setUnusualAmount(result.getUnusualAmount());
        transaction.setUnusualTime(result.getUnusualTime());
        transaction.setUnusualLocation(result.getUnusualLocation());
        transaction.setUnusualDevice(result.getUnusualDevice());

        transactionRepository.save(transaction);
    }

    /**
     * Create fraud alert
     */
    private void createFraudAlert(Transaction transaction, FraudDetectionResult result) {
        try {
            FraudAlert alert = new FraudAlert();
            alert.setTransactionId(transaction.getTransactionId());
            alert.setUserId(transaction.getUserId());
            alert.setAlertType(result.getDetectionMethod());
            alert.setFraudScore(result.getFraudScore());
            alert.setReason(result.getPrimaryReason());

            // Set severity
            if (result.getFraudScore() >= 0.9) {
                alert.setSeverity("CRITICAL");
                alert.setAction("BLOCK");
            } else if (result.getFraudScore() >= 0.7) {
                alert.setSeverity("HIGH");
                alert.setAction("REVIEW");
            } else if (result.getFraudScore() >= 0.5) {
                alert.setSeverity("MEDIUM");
                alert.setAction("REVIEW");
            } else {
                alert.setSeverity("LOW");
                alert.setAction("ALLOW_WITH_WARNING");
            }

            // Store triggered rules as JSON
            alert.setRulesFired(objectMapper.writeValueAsString(result.getTriggeredRules()));

            fraudAlertRepository.save(alert);

            log.info("Fraud alert created for transaction: {}", transaction.getTransactionId());
        } catch (Exception e) {
            log.error("Error creating fraud alert", e);
        }
    }

    /**
     * Update user trust score based on fraud detection result
     */
    private void updateUserTrustScore(User user, FraudDetectionResult result) {
        double currentScore = user.getTrustScore();
        double newScore;

        if (result.getFraudScore() >= 0.7) {
            // Fraud detected - decrease trust significantly
            newScore = Math.max(0, currentScore - 20);
            user.setFraudCount(user.getFraudCount() + 1);
        } else if (result.getFraudScore() >= 0.4) {
            // Suspicious - decrease trust moderately
            newScore = Math.max(0, currentScore - 5);
        } else {
            // Normal transaction - slowly increase trust
            newScore = Math.min(100, currentScore + 0.5);
        }

        user.setTrustScore(newScore);
        userRepository.save(user);

        log.debug("Updated trust score for user {}: {} -> {}",
                user.getUserId(), currentScore, newScore);
    }

    /**
     * Block user account due to fraud
     */
    private void blockUserAccount(String userId) {
        userRepository.findByUserId(userId).ifPresent(user -> {
            user.setAccountLocked(true);
            user.setEnabled(false);
            userRepository.save(user);
            log.warn("User account blocked due to fraud: {}", userId);
        });
    }

    /**
     * Calculate behavior-based score
     */
    private Double calculateBehaviorScore(UserBehavior behavior) {
        if (behavior == null) {
            return 0.5;
        }

        // Combine consistency and other behavioral metrics
        double consistencyScore = behavior.getConsistencyScore() != null
                ? behavior.getConsistencyScore()
                : 0.5;

        double failedPenalty = behavior.getFailedAttempts() != null
                ? Math.min(behavior.getFailedAttempts() * 0.1, 0.5)
                : 0.0;

        return Math.max(0.0, consistencyScore - failedPenalty);
    }

    /**
     * Asynchronously update user behavior
     */
    @Async
    public void updateBehaviorAsync(String userId) {
        try {
            behaviorService.updateBehavior(userId);
        } catch (Exception e) {
            log.error("Error updating behavior asynchronously for user: {}", userId, e);
        }
    }

    /**
     * Get fraud statistics for a user
     */
    public FraudStatistics getUserFraudStatistics(String userId) {
        User user = userRepository.findByUserId(userId).orElse(null);
        List<FraudAlert> alerts = fraudAlertRepository.findByUserId(userId);
        Long fraudCount = transactionRepository.countFraudulentTransactions(userId);

        return FraudStatistics.builder()
                .userId(userId)
                .trustScore(user != null ? user.getTrustScore() : 0.0)
                .totalFraudAlerts(alerts.size())
                .fraudulentTransactions(fraudCount.intValue())
                .accountLocked(user != null && user.getAccountLocked())
                .build();
    }

    // Inner class for fraud statistics
    @lombok.Data
    @lombok.Builder
    public static class FraudStatistics {
        private String userId;
        private Double trustScore;
        private Integer totalFraudAlerts;
        private Integer fraudulentTransactions;
        private Boolean accountLocked;
    }
}