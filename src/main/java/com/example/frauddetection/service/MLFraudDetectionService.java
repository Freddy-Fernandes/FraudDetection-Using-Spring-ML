package com.example.frauddetection.service;

import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.User;
import com.example.frauddetection.entity.UserBehavior;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Advanced ML-Inspired Fraud Detection Service
 * Pure Java implementation based on ML model insights
 * 
 * Compatible with existing User and UserBehavior entities
 */
@Service
@Slf4j
public class MLFraudDetectionService {

    // Feature weights (learned from ML model)
    private static final double MERCHANT_WEIGHT = 0.20;
    private static final double RISK_WEIGHT = 0.17;
    private static final double VELOCITY_WEIGHT = 0.13;
    private static final double DEVICE_WEIGHT = 0.12;
    private static final double TRUST_WEIGHT = 0.11;
    private static final double AMOUNT_WEIGHT = 0.08;
    private static final double TIME_WEIGHT = 0.07;
    private static final double ACCOUNT_WEIGHT = 0.06;
    private static final double FAILED_ATTEMPTS_WEIGHT = 0.06;

    /**
     * Predict fraud probability using ML-inspired weighted scoring
     * Returns probability between 0.0 and 1.0
     */
    public double predictFraudProbability(Transaction transaction, UserBehavior behavior, User user) {
        try {
            double fraudScore = 0.0;

            // 1. MERCHANT HISTORY (20% - not in current schema, use consistency as proxy)
            fraudScore += calculateMerchantScore(behavior) * MERCHANT_WEIGHT;

            // 2. RISK SCORE (17% - from User trust score)
            fraudScore += calculateRiskScore(user) * RISK_WEIGHT;

            // 3. VELOCITY SCORE (13% - from transaction frequency)
            fraudScore += calculateVelocityScore(transaction, behavior) * VELOCITY_WEIGHT;

            // 4. DEVICE AGE (12% - estimate from account age)
            fraudScore += calculateDeviceScore(user) * DEVICE_WEIGHT;

            // 5. TRUST SCORE (11% - from User)
            fraudScore += calculateTrustScore(user) * TRUST_WEIGHT;

            // 6. AMOUNT ANOMALY (8%)
            fraudScore += calculateAmountScore(transaction, behavior) * AMOUNT_WEIGHT;

            // 7. TIME ANOMALY (7%)
            fraudScore += calculateTimeScore(transaction) * TIME_WEIGHT;

            // 8. ACCOUNT AGE (6%)
            fraudScore += calculateAccountAgeScore(user) * ACCOUNT_WEIGHT;

            // 9. FAILED ATTEMPTS (6%)
            fraudScore += calculateFailedAttemptsScore(behavior) * FAILED_ATTEMPTS_WEIGHT;

            // Normalize to 0.0-1.0 range
            fraudScore = Math.max(0.0, Math.min(1.0, fraudScore));

            log.debug("ML Fraud Prediction for tx {}: score={}",
                    transaction.getTransactionId(), fraudScore);

            return fraudScore;

        } catch (Exception e) {
            log.error("Error in ML fraud prediction: {}", e.getMessage(), e);
            return 0.5; // Return neutral score on error
        }
    }

    /**
     * MERCHANT/CONSISTENCY SCORE (20%)
     * Uses consistency score as proxy for merchant familiarity
     */
    private double calculateMerchantScore(UserBehavior behavior) {
        if (behavior == null || behavior.getConsistencyScore() == null) {
            return 0.6; // Unknown = medium risk
        }

        // Invert consistency - low consistency = higher risk
        double consistency = behavior.getConsistencyScore();

        if (consistency < 0.3)
            return 1.0; // Very inconsistent = new pattern
        if (consistency < 0.5)
            return 0.7;
        if (consistency < 0.7)
            return 0.4;
        return 0.0; // High consistency = familiar pattern
    }

    /**
     * RISK SCORE (17%)
     * From User trust score
     */
    private double calculateRiskScore(User user) {
        if (user == null || user.getTrustScore() == null) {
            return 0.5;
        }

        double trustScore = user.getTrustScore();
        return (100 - trustScore) / 100.0; // Invert: low trust = high risk
    }

    /**
     * VELOCITY SCORE (13%)
     * From UserBehavior transaction frequency
     */
    private double calculateVelocityScore(Transaction transaction, UserBehavior behavior) {
        if (behavior == null)
            return 0.3;

        double velocityScore = 0.0;

        // Check transactions in last hour (from Transaction entity)
        Integer txLastHour = transaction.getTransactionsInLastHour();
        if (txLastHour != null) {
            if (txLastHour >= 15)
                velocityScore += 1.0;
            else if (txLastHour >= 10)
                velocityScore += 0.8;
            else if (txLastHour >= 5)
                velocityScore += 0.5;
            else if (txLastHour >= 3)
                velocityScore += 0.2;
        }

        // Check daily frequency
        Integer txPerDay = behavior.getTransactionsPerDay();
        if (txPerDay != null) {
            if (txPerDay >= 50)
                velocityScore += 0.5;
            else if (txPerDay >= 30)
                velocityScore += 0.3;
            else if (txPerDay >= 15)
                velocityScore += 0.1;
        }

        return Math.min(velocityScore, 1.0);
    }

    /**
     * DEVICE AGE SCORE (12%)
     * Estimate from account age and behavior patterns
     */
    private double calculateDeviceScore(User user) {
        if (user == null || user.getCreatedAt() == null) {
            return 0.5;
        }

        // Use account age as proxy for device age
        long accountAgeDays = ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now());

        if (accountAgeDays < 1)
            return 1.0;
        if (accountAgeDays < 7)
            return 0.8;
        if (accountAgeDays < 30)
            return 0.5;
        if (accountAgeDays < 90)
            return 0.2;
        return 0.0;
    }

    /**
     * TRUST SCORE (11%)
     */
    private double calculateTrustScore(User user) {
        if (user == null || user.getTrustScore() == null) {
            return 0.5;
        }

        double trustScore = user.getTrustScore();

        if (trustScore < 30)
            return 1.0;
        if (trustScore < 50)
            return 0.7;
        if (trustScore < 70)
            return 0.4;
        if (trustScore < 85)
            return 0.1;
        return 0.0;
    }

    /**
     * AMOUNT ANOMALY SCORE (8%)
     */
    private double calculateAmountScore(Transaction transaction, UserBehavior behavior) {
        double amount = transaction.getAmount();

        // High absolute amounts
        if (amount > 5000)
            return 1.0;
        if (amount > 2000)
            return 0.7;
        if (amount > 1000)
            return 0.4;

        // Deviation from average (if available)
        if (behavior != null && behavior.getAvgTransactionAmount() != null) {
            double avgAmount = behavior.getAvgTransactionAmount();
            if (avgAmount > 0) {
                double ratio = amount / avgAmount;
                if (ratio > 10)
                    return 0.8;
                if (ratio > 5)
                    return 0.5;
                if (ratio > 3)
                    return 0.2;
            }
        }

        // Round amounts (often fraud)
        if (amount >= 100 && amount % 100 == 0)
            return 0.1;

        return 0.0;
    }

    /**
     * TIME ANOMALY SCORE (7%)
     */
    private double calculateTimeScore(Transaction transaction) {
        LocalDateTime time = transaction.getTransactionTime();
        int hour = time.getHour();

        // Late night (2 AM - 5 AM)
        if (hour >= 2 && hour <= 5)
            return 0.8;

        // Very early (midnight - 2 AM)
        if (hour >= 0 && hour <= 2)
            return 0.5;

        // Very late (11 PM - midnight)
        if (hour >= 23)
            return 0.3;

        return 0.0;
    }

    /**
     * ACCOUNT AGE SCORE (6%)
     */
    private double calculateAccountAgeScore(User user) {
        if (user == null || user.getCreatedAt() == null) {
            return 0.5;
        }

        long accountAgeDays = ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now());

        if (accountAgeDays < 1)
            return 1.0;
        if (accountAgeDays < 7)
            return 0.8;
        if (accountAgeDays < 30)
            return 0.5;
        if (accountAgeDays < 90)
            return 0.2;
        return 0.0;
    }

    /**
     * FAILED ATTEMPTS SCORE (6%)
     */
    private double calculateFailedAttemptsScore(UserBehavior behavior) {
        if (behavior == null || behavior.getFailedAttempts() == null) {
            return 0.0;
        }

        int failedAttempts = behavior.getFailedAttempts();

        if (failedAttempts >= 5)
            return 1.0;
        if (failedAttempts >= 3)
            return 0.7;
        if (failedAttempts >= 2)
            return 0.4;
        if (failedAttempts >= 1)
            return 0.2;
        return 0.0;
    }

    /**
     * Get fraud score on 0-10 scale
     */
    public double getFraudScore(Transaction transaction, UserBehavior behavior, User user) {
        return predictFraudProbability(transaction, behavior, user) * 10.0;
    }

    /**
     * Get human-readable explanation
     */
    public String getExplanation(Transaction transaction, UserBehavior behavior, User user) {
        StringBuilder explanation = new StringBuilder();
        double totalScore = predictFraudProbability(transaction, behavior, user);

        if (totalScore < 0.3) {
            explanation.append("Low risk transaction. ");
        } else if (totalScore < 0.7) {
            explanation.append("Moderate risk detected. ");
        } else {
            explanation.append("High risk detected! ");
        }

        // Add specific factors
        if (calculateMerchantScore(behavior) > 0.5) {
            explanation.append("Unusual transaction pattern. ");
        }
        if (calculateVelocityScore(transaction, behavior) > 0.5) {
            explanation.append("High transaction frequency. ");
        }
        if (calculateDeviceScore(user) > 0.5) {
            explanation.append("New account or device. ");
        }
        if (calculateTrustScore(user) > 0.5) {
            explanation.append("Low user trust score. ");
        }
        if (calculateAmountScore(transaction, behavior) > 0.5) {
            explanation.append("Unusual transaction amount. ");
        }
        if (calculateTimeScore(transaction) > 0.5) {
            explanation.append("Unusual transaction time. ");
        }
        if (calculateFailedAttemptsScore(behavior) > 0.5) {
            explanation.append("Recent failed transaction attempts. ");
        }
        return explanation.toString().trim();
    }
}