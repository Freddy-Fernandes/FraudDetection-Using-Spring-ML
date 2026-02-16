package com.example.frauddetection.controller;


import com.example.frauddetection.entity.FraudAlert;
import com.example.frauddetection.repository.FraudAlertRepository;
import com.example.frauddetection.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final FraudAlertRepository fraudAlertRepository;

    /**
     * Get fraud statistics for a user
     * GET /api/fraud/statistics/{userId}
     */
    @GetMapping("/statistics/{userId}")
    public ResponseEntity<FraudDetectionService.FraudStatistics> getUserFraudStatistics(
            @PathVariable String userId) {
        log.info("Fetching fraud statistics for user: {}", userId);

        try {
            FraudDetectionService.FraudStatistics stats = fraudDetectionService.getUserFraudStatistics(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error fetching fraud statistics", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get fraud alerts for a user
     * GET /api/fraud/alerts/{userId}
     */
    @GetMapping("/alerts/{userId}")
    public ResponseEntity<List<FraudAlert>> getUserFraudAlerts(@PathVariable String userId) {
        log.info("Fetching fraud alerts for user: {}", userId);

        try {
            List<FraudAlert> alerts = fraudAlertRepository.findByUserId(userId);
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Error fetching fraud alerts", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get unreviewed fraud alerts
     * GET /api/fraud/alerts/unreviewed
     */
    @GetMapping("/alerts/unreviewed")
    public ResponseEntity<List<FraudAlert>> getUnreviewedAlerts() {
        log.info("Fetching unreviewed fraud alerts");

        try {
            List<FraudAlert> alerts = fraudAlertRepository.findByReviewedFalse();
            return ResponseEntity.ok(alerts);
        } catch (Exception e) {
            log.error("Error fetching unreviewed alerts", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Review a fraud alert
     * PUT /api/fraud/alerts/{alertId}/review
     */
    @PutMapping("/alerts/{alertId}/review")
    public ResponseEntity<FraudAlert> reviewAlert(
            @PathVariable Long alertId,
            @RequestParam String reviewedBy,
            @RequestParam Boolean confirmedFraud,
            @RequestParam(required = false) String comments) {
        log.info("Reviewing fraud alert: {}", alertId);

        try {
            FraudAlert alert = fraudAlertRepository.findById(alertId)
                    .orElseThrow(() -> new RuntimeException("Alert not found"));

            alert.setReviewed(true);
            alert.setReviewedBy(reviewedBy);
            alert.setConfirmedFraud(confirmedFraud);
            alert.setReviewComments(comments);
            alert.setReviewedAt(java.time.LocalDateTime.now());

            FraudAlert updated = fraudAlertRepository.save(alert);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error reviewing alert", e);
            return ResponseEntity.status(500).build();
        }
    }
}