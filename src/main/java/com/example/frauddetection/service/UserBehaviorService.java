package com.example.frauddetection.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.UserBehavior;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.repository.UserBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserBehaviorService {

    private final UserBehaviorRepository behaviorRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get or create user behavior profile
     */
    public UserBehavior getUserBehavior(String userId) {
        return behaviorRepository.findByUserId(userId)
                .orElseGet(() -> createInitialBehavior(userId));
    }

    /**
     * Create initial behavior profile for new user
     */
    private UserBehavior createInitialBehavior(String userId) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setConsistencyScore(0.5); // Neutral initial score
        behavior.setDiversityScore(0.5);
        behavior.setVelocityPattern(0.5);
        behavior.setFailedAttempts(0);
        behavior.setChargebacks(0);
        behavior.setDisputedTransactions(0);
        behavior.setDataPointsCount(0);
        behavior.setKnownDevices("[]");
        behavior.setKnownIpAddresses("[]");
        behavior.setFrequentLocations("[]");
        behavior.setFrequentCountries("[]");
        behavior.setFrequentMerchants("[]");
        behavior.setFrequentCategories("[]");
        behavior.setPreferredTransactionHours("[]");
        behavior.setPreferredTransactionDays("[]");

        return behaviorRepository.save(behavior);
    }

    /**
     * Update user behavior based on transaction history
     */
    @Transactional
    public void updateBehavior(String userId) {
        try {
            List<Transaction> transactions = transactionRepository
                    .findByUserIdOrderByTransactionTimeDesc(userId);

            if (transactions.isEmpty()) {
                log.debug("No transactions found for user: {}", userId);
                return;
            }

            UserBehavior behavior = getUserBehavior(userId);

            // Filter only successful transactions for behavior analysis
            List<Transaction> successfulTrans = transactions.stream()
                    .filter(t -> "SAFE".equals(t.getFraudStatus()) || "APPROVED".equals(t.getStatus()))
                    .collect(Collectors.toList());

            if (successfulTrans.isEmpty()) {
                return;
            }

            // Update transaction statistics
            updateTransactionStatistics(behavior, successfulTrans);

            // Update time patterns
            updateTimePatterns(behavior, successfulTrans);

            // Update location patterns
            updateLocationPatterns(behavior, successfulTrans);

            // Update device patterns
            updateDevicePatterns(behavior, successfulTrans);

            // Update merchant patterns
            updateMerchantPatterns(behavior, successfulTrans);

            // Calculate behavioral scores
            calculateBehavioralScores(behavior, successfulTrans);

            behavior.setDataPointsCount(successfulTrans.size());
            behaviorRepository.save(behavior);

            log.info("Updated behavior profile for user: {}", userId);
        } catch (Exception e) {
            log.error("Error updating user behavior for user: {}", userId, e);
        }
    }

    /**
     * Update transaction amount statistics
     */
    private void updateTransactionStatistics(UserBehavior behavior, List<Transaction> transactions) {
        DescriptiveStatistics stats = new DescriptiveStatistics();

        for (Transaction t : transactions) {
            stats.addValue(t.getAmount());
        }

        behavior.setAvgTransactionAmount(stats.getMean());
        behavior.setMaxTransactionAmount(stats.getMax());
        behavior.setMinTransactionAmount(stats.getMin());
        behavior.setStdDevTransactionAmount(stats.getStandardDeviation());

        // Calculate transactions per period
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        LocalDateTime weekAgo = now.minusWeeks(1);
        LocalDateTime monthAgo = now.minusMonths(1);

        long transPerDay = transactions.stream()
                .filter(t -> t.getTransactionTime().isAfter(dayAgo))
                .count();
        long transPerWeek = transactions.stream()
                .filter(t -> t.getTransactionTime().isAfter(weekAgo))
                .count();
        long transPerMonth = transactions.stream()
                .filter(t -> t.getTransactionTime().isAfter(monthAgo))
                .count();

        behavior.setTransactionsPerDay((int) transPerDay);
        behavior.setTransactionsPerWeek((int) transPerWeek);
        behavior.setTransactionsPerMonth((int) transPerMonth);
    }

    /**
     * Update time patterns (preferred hours and days)
     */
    private void updateTimePatterns(UserBehavior behavior, List<Transaction> transactions) {
        try {
            // Count transactions by hour
            Map<Integer, Long> hourFrequency = transactions.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getTransactionTime().getHour(),
                            Collectors.counting()));

            // Get top 3 hours
            List<Integer> preferredHours = hourFrequency.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            behavior.setPreferredTransactionHours(objectMapper.writeValueAsString(preferredHours));

            // Count transactions by day of week
            Map<Integer, Long> dayFrequency = transactions.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getTransactionTime().getDayOfWeek().getValue(),
                            Collectors.counting()));

            // Get top 3 days
            List<Integer> preferredDays = dayFrequency.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            behavior.setPreferredTransactionDays(objectMapper.writeValueAsString(preferredDays));
        } catch (JsonProcessingException e) {
            log.error("Error updating time patterns", e);
        }
    }

    /**
     * Update location patterns
     */
    private void updateLocationPatterns(UserBehavior behavior, List<Transaction> transactions) {
        try {
            // Get unique countries
            Set<String> countries = transactions.stream()
                    .map(Transaction::getCountry)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            behavior.setFrequentCountries(objectMapper.writeValueAsString(countries));

            // Get unique cities
            Map<String, Long> cityFrequency = transactions.stream()
                    .filter(t -> t.getCity() != null)
                    .collect(Collectors.groupingBy(
                            Transaction::getCity,
                            Collectors.counting()));

            List<String> frequentCities = cityFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            behavior.setFrequentLocations(objectMapper.writeValueAsString(frequentCities));
        } catch (JsonProcessingException e) {
            log.error("Error updating location patterns", e);
        }
    }

    /**
     * Update device patterns
     */
    private void updateDevicePatterns(UserBehavior behavior, List<Transaction> transactions) {
        try {
            // Get unique devices
            Set<String> devices = transactions.stream()
                    .map(Transaction::getDeviceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            behavior.setKnownDevices(objectMapper.writeValueAsString(devices));

            // Get unique IP addresses
            Set<String> ips = transactions.stream()
                    .map(Transaction::getIpAddress)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            behavior.setKnownIpAddresses(objectMapper.writeValueAsString(ips));
        } catch (JsonProcessingException e) {
            log.error("Error updating device patterns", e);
        }
    }

    /**
     * Update merchant patterns
     */
    private void updateMerchantPatterns(UserBehavior behavior, List<Transaction> transactions) {
        try {
            // Get frequent merchants
            Map<String, Long> merchantFrequency = transactions.stream()
                    .filter(t -> t.getMerchantId() != null)
                    .collect(Collectors.groupingBy(
                            Transaction::getMerchantId,
                            Collectors.counting()));

            List<String> frequentMerchants = merchantFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            behavior.setFrequentMerchants(objectMapper.writeValueAsString(frequentMerchants));

            // Get frequent categories
            Map<String, Long> categoryFrequency = transactions.stream()
                    .filter(t -> t.getMerchantCategory() != null)
                    .collect(Collectors.groupingBy(
                            Transaction::getMerchantCategory,
                            Collectors.counting()));

            List<String> frequentCategories = categoryFrequency.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            behavior.setFrequentCategories(objectMapper.writeValueAsString(frequentCategories));
        } catch (JsonProcessingException e) {
            log.error("Error updating merchant patterns", e);
        }
    }

    /**
     * Calculate behavioral consistency and diversity scores
     */
    private void calculateBehavioralScores(UserBehavior behavior, List<Transaction> transactions) {
        // Consistency score: how predictable is user behavior (0-1)
        // Higher consistency = more predictable patterns
        double consistencyScore = calculateConsistencyScore(transactions);
        behavior.setConsistencyScore(consistencyScore);

        // Diversity score: variety in transaction patterns (0-1)
        double diversityScore = calculateDiversityScore(transactions);
        behavior.setDiversityScore(diversityScore);

        // Velocity pattern: normal transaction frequency
        double velocityPattern = calculateVelocityPattern(transactions);
        behavior.setVelocityPattern(velocityPattern);
    }

    private double calculateConsistencyScore(List<Transaction> transactions) {
        if (transactions.size() < 10) {
            return 0.5; // Not enough data
        }

        // Calculate consistency based on amount variance
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Transaction t : transactions) {
            stats.addValue(t.getAmount());
        }

        double coefficientOfVariation = stats.getStandardDeviation() / stats.getMean();

        // Lower CV = higher consistency
        return Math.max(0, 1.0 - Math.min(coefficientOfVariation, 1.0));
    }

    private double calculateDiversityScore(List<Transaction> transactions) {
        Set<String> uniqueMerchants = transactions.stream()
                .map(Transaction::getMerchantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> uniqueCategories = transactions.stream()
                .map(Transaction::getMerchantCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // More unique merchants/categories = higher diversity
        double merchantDiversity = Math.min(uniqueMerchants.size() / 20.0, 1.0);
        double categoryDiversity = Math.min(uniqueCategories.size() / 10.0, 1.0);

        return (merchantDiversity + categoryDiversity) / 2.0;
    }

    private double calculateVelocityPattern(List<Transaction> transactions) {
        if (transactions.size() < 2) {
            return 0.5;
        }

        // Calculate average time between transactions
        List<Long> intervals = new ArrayList<>();
        for (int i = 0; i < transactions.size() - 1; i++) {
            long interval = ChronoUnit.SECONDS.between(
                    transactions.get(i + 1).getTransactionTime(),
                    transactions.get(i).getTransactionTime());
            intervals.add(Math.abs(interval));
        }

        double avgInterval = intervals.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(86400.0); // Default to 1 day

        // Normalize to 0-1 (assuming typical range is 1 hour to 1 week)
        return Math.min(avgInterval / 604800.0, 1.0); // 604800 = seconds in a week
    }
}