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
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final UserRepository userRepository;
    private final UserBehaviorService behaviorService;
    private final MLFraudDetectionService mlService;

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

            // 4. Build result using the builder pattern
            FraudDetectionResult result = FraudDetectionResult.builder()
                    .fraudScore(mlScore)
                    .mlScore(mlScore)
                    .detectionMethod("ML")
                    .userTrustScore(user.getTrustScore())
                    .userFraudHistory(user.getFraudCount())
                    .build();

            // 5. Calculate status using helper methods
            result.setFraudStatus(result.calculateFraudStatus());
            result.setRiskLevel(result.calculateRiskLevel());
            result.setRecommendation(result.calculateRecommendation());
            result.setIsFraud(mlScore > 7.0);

            // 6. Get explanation from ML service
            String explanation = mlService.getExplanation(transaction, behavior, user);
            result.setPrimaryReason(explanation);
            result.addReason(explanation);

            return result;

        } catch (Exception e) {
            log.error("Fraud detection failed: {}", e.getMessage(), e);
            throw new RuntimeException("Fraud detection failed", e);
        }
    }
}