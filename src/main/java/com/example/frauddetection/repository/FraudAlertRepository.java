package com.example.frauddetection.repository;

import com.example.frauddetection.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    List<FraudAlert> findByUserId(String userId);

    List<FraudAlert> findByTransactionId(String transactionId);

    List<FraudAlert> findByReviewedFalse();

    @Query("SELECT f FROM FraudAlert f WHERE f.severity = :severity AND f.reviewed = false")
    List<FraudAlert> findUnreviewedBySeverity(@Param("severity") String severity);

    @Query("SELECT COUNT(f) FROM FraudAlert f WHERE f.userId = :userId AND f.detectedAt >= :since")
    Long countAlertsSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);
}