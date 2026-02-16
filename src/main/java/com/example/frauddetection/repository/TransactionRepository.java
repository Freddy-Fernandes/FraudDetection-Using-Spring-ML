package com.example.frauddetection.repository;

import com.example.frauddetection.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionId(String transactionId);

    List<Transaction> findByUserId(String userId);

    List<Transaction> findByUserIdOrderByTransactionTimeDesc(String userId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.transactionTime >= :since")
    List<Transaction> findByUserIdAndTransactionTimeAfter(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.transactionTime >= :since")
    Long countTransactionsSince(
            @Param("userId") String userId,
            @Param("since") LocalDateTime since);

    @Query("SELECT AVG(t.amount) FROM Transaction t WHERE t.userId = :userId AND t.fraudStatus = 'SAFE'")
    Double getAverageTransactionAmount(@Param("userId") String userId);

    @Query("SELECT STDDEV(t.amount) FROM Transaction t WHERE t.userId = :userId AND t.fraudStatus = 'SAFE'")
    Double getStandardDeviationAmount(@Param("userId") String userId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.deviceId = :deviceId")
    List<Transaction> findByUserIdAndDeviceId(
            @Param("userId") String userId,
            @Param("deviceId") String deviceId);

    @Query("SELECT DISTINCT t.deviceId FROM Transaction t WHERE t.userId = :userId")
    List<String> findDistinctDevicesByUserId(@Param("userId") String userId);

    @Query("SELECT DISTINCT t.country FROM Transaction t WHERE t.userId = :userId")
    List<String> findDistinctCountriesByUserId(@Param("userId") String userId);

    @Query("SELECT t FROM Transaction t WHERE t.fraudStatus IN ('SUSPICIOUS', 'FRAUD')")
    List<Transaction> findSuspiciousTransactions();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.fraudStatus = 'FRAUD'")
    Long countFraudulentTransactions(@Param("userId") String userId);
}