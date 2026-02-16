package com.example.frauddetection.service;


import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.User;
import com.example.frauddetection.entity.UserBehavior;
import com.example.frauddetection.model.FraudDetectionResult;
import com.example.frauddetection.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuleBasedFraudDetectionService {

    private final TransactionRepository transactionRepository;

    @Value("${fraud.max.transaction.amount:10000}")
    private double maxTransactionAmount;

    @Value("${fraud.max.transactions.per.hour:10}")
    private int maxTransactionsPerHour;

    @Value("${fraud.max.transactions.per.day:50}")
    private int maxTransactionsPerDay;

    /**
     * Apply rule-based fraud detection
     */
    public FraudDetectionResult applyRules(Transaction transaction, User user, UserBehavior behavior) {
        FraudDetectionResult result = FraudDetectionResult.builder()
                .isFraud(false)
                .fraudScore(0.0)
                .riskLevel("LOW")
                .build();

        double ruleScore = 0.0;

        // Rule 1: Check for abnormally high transaction amount
        if (checkHighAmount(transaction, behavior)) {
            ruleScore += 0.3;
            result.addTriggeredRule("HIGH_AMOUNT");
            result.addReason("Transaction amount significantly higher than user's average");
            result.setUnusualAmount(true);
        }

        // Rule 2: Check transaction velocity (frequency)
        if (checkHighVelocity(transaction)) {
            ruleScore += 0.25;
            result.addTriggeredRule("HIGH_VELOCITY");
            result.addReason("Too many transactions in short time period");
            result.setHighVelocity(true);
        }

        // Rule 3: Check for unusual transaction time
        if (checkUnusualTime(transaction, behavior)) {
            ruleScore += 0.15;
            result.addTriggeredRule("UNUSUAL_TIME");
            result.addReason("Transaction at unusual hour for this user");
            result.setUnusualTime(true);
        }

        // Rule 4: Check for new or unusual location
        if (checkUnusualLocation(transaction, behavior)) {
            ruleScore += 0.2;
            result.addTriggeredRule("UNUSUAL_LOCATION");
            result.addReason("Transaction from new or unusual location");
            result.setUnusualLocation(true);
        }

        // Rule 5: Check for new device
        if (checkNewDevice(transaction, behavior)) {
            ruleScore += 0.15;
            result.addTriggeredRule("NEW_DEVICE");
            result.addReason("Transaction from unrecognized device");
            result.setUnusualDevice(true);
            result.setNewDevice(true);
        }

        // Rule 6: Check user trust score
        if (user != null && user.getTrustScore() < 50) {
            ruleScore += 0.2;
            result.addTriggeredRule("LOW_TRUST_SCORE");
            result.addReason("User has low trust score");
        }

        // Rule 7: Check for account age
        if (user != null && isNewAccount(user)) {
            ruleScore += 0.1;
            result.addTriggeredRule("NEW_ACCOUNT");
            result.addReason("Transaction from new account");
        }

        // Rule 8: Check for multiple failed attempts
        if (behavior != null && behavior.getFailedAttempts() != null && behavior.getFailedAttempts() > 3) {
            ruleScore += 0.15;
            result.addTriggeredRule("MULTIPLE_FAILED_ATTEMPTS");
            result.addReason("Multiple failed transaction attempts recently");
        }

        // Rule 9: Round amount check (fraudsters often use round amounts)
        if (isRoundAmount(transaction.getAmount())) {
            ruleScore += 0.05;
            result.addTriggeredRule("ROUND_AMOUNT");
            result.addReason("Suspiciously round transaction amount");
        }

        // Rule 10: Maximum amount exceeded
        if (transaction.getAmount() > maxTransactionAmount) {
            ruleScore += 0.4;
            result.addTriggeredRule("AMOUNT_LIMIT_EXCEEDED");
            result.addReason("Transaction amount exceeds maximum limit");
        }

        // Normalize score to 0-1 range
        result.setRuleBasedScore(Math.min(ruleScore, 1.0));
        result.setIsFraud(ruleScore >= 0.7);

        if (result.getTriggeredRules().isEmpty()) {
            result.setPrimaryReason("No suspicious patterns detected");
        } else {
            result.setPrimaryReason(result.getAllReasons().get(0));
        }

        log.debug("Rule-based detection score for transaction {}: {}",
                transaction.getTransactionId(), result.getRuleBasedScore());

        return result;
    }

    /**
     * Check if transaction amount is abnormally high
     */
    private boolean checkHighAmount(Transaction transaction, UserBehavior behavior) {
        if (behavior == null || behavior.getAvgTransactionAmount() == null) {
            return transaction.getAmount() > 5000; // Default threshold
        }

        double avgAmount = behavior.getAvgTransactionAmount();
        double stdDev = behavior.getStdDevTransactionAmount() != null
                ? behavior.getStdDevTransactionAmount()
                : avgAmount * 0.5;

        // Flag if amount is more than 3 standard deviations from mean
        double threshold = avgAmount + (3 * stdDev);
        return transaction.getAmount() > threshold;
    }

    /**
     * Check transaction velocity (too many transactions in short time)
     */
    private boolean checkHighVelocity(Transaction transaction) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        Long transactionsLastHour = transactionRepository.countTransactionsSince(
                transaction.getUserId(), oneHourAgo);
        Long transactionsLastDay = transactionRepository.countTransactionsSince(
                transaction.getUserId(), oneDayAgo);

        transaction.setTransactionsInLastHour(transactionsLastHour.intValue());
        transaction.setTransactionsInLastDay(transactionsLastDay.intValue());

        return transactionsLastHour > maxTransactionsPerHour ||
                transactionsLastDay > maxTransactionsPerDay;
    }

    /**
     * Check if transaction is at an unusual time for this user
     */
    private boolean checkUnusualTime(Transaction transaction, UserBehavior behavior) {
        LocalTime transTime = transaction.getTransactionTime().toLocalTime();
        int hour = transTime.getHour();

        // Transactions between 2 AM and 6 AM are generally suspicious
        if (hour >= 2 && hour < 6) {
            return true;
        }

        // Check against user's typical transaction hours
        if (behavior != null && behavior.getPreferredTransactionHours() != null) {
            // This would check against stored patterns
            // Simplified for this implementation
            return false;
        }

        return false;
    }

    /**
     * Check if transaction is from an unusual location
     */
    private boolean checkUnusualLocation(Transaction transaction, UserBehavior behavior) {
        if (transaction.getCountry() == null) {
            return false;
        }

        if (behavior == null || behavior.getFrequentCountries() == null) {
            return false; // Can't determine without history
        }

        // Check if country is in user's frequent locations
        String frequentCountries = behavior.getFrequentCountries();
        boolean isKnownCountry = frequentCountries.contains(transaction.getCountry());

        return !isKnownCountry;
    }

    /**
     * Check if transaction is from a new device
     */
    private boolean checkNewDevice(Transaction transaction, UserBehavior behavior) {
        if (transaction.getDeviceId() == null) {
            return false;
        }

        List<String> knownDevices = transactionRepository.findDistinctDevicesByUserId(
                transaction.getUserId());

        return !knownDevices.contains(transaction.getDeviceId());
    }

    /**
     * Check if account is newly created (less than 7 days old)
     */
    private boolean isNewAccount(User user) {
        if (user.getRegistrationDate() == null) {
            return false;
        }

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return user.getRegistrationDate().isAfter(sevenDaysAgo);
    }

    /**
     * Check if amount is suspiciously round (e.g., 1000, 5000)
     */
    private boolean isRoundAmount(double amount) {
        // Check if amount is a multiple of 1000 or 500
        return amount >= 500 && (amount % 1000 == 0 || amount % 500 == 0);
    }

    /**
     * Calculate deviation from normal behavior
     */
    public double calculateBehaviorDeviation(Transaction transaction, UserBehavior behavior) {
        if (behavior == null || behavior.getAvgTransactionAmount() == null) {
            return 0.0;
        }

        double avgAmount = behavior.getAvgTransactionAmount();
        double stdDev = behavior.getStdDevTransactionAmount() != null
                ? behavior.getStdDevTransactionAmount()
                : avgAmount * 0.5;

        if (stdDev == 0) {
            return 0.0;
        }

        return Math.abs(transaction.getAmount() - avgAmount) / stdDev;
    }
}