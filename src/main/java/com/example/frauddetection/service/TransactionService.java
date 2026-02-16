package com.example.frauddetection.service;

import com.example.frauddetection.dto.RegisterRequest;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.dto.TransactionResponse;
import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.User;
import com.example.frauddetection.model.FraudDetectionResult;
import com.example.frauddetection.repository.TransactionRepository;
import com.example.frauddetection.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final FraudDetectionService fraudDetectionService;

    /**
     * Process a transaction with fraud detection
     */
    @Transactional
    public TransactionResponse processTransaction(TransactionRequest request) {
        log.info("Processing transaction for user: {}", request.getUserId());

        // Validate user exists
        User user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));

        // Check if account is locked
        if (user.getAccountLocked()) {
            return buildDeclinedResponse("Account is locked due to fraud", request);
        }

        // Create transaction entity
        Transaction transaction = buildTransaction(request);

        // Calculate behavioral features
        enrichTransactionWithBehavioralData(transaction);

        // Save transaction initially
        transaction = transactionRepository.save(transaction);

        // Run pre-transaction fraud check
        FraudDetectionResult fraudResult = fraudDetectionService.preTransactionCheck(transaction);

        // Update transaction status
        transaction = transactionRepository.save(transaction);

        // Build response
        TransactionResponse response = buildResponse(transaction, fraudResult);

        log.info("Transaction processed: ID={}, Status={}, FraudScore={}",
                transaction.getTransactionId(), transaction.getStatus(), fraudResult.getFraudScore());

        return response;
    }

    /**
     * Process QR code transaction
     */
    @Transactional
    public TransactionResponse processQRTransaction(TransactionRequest request) {
        log.info("Processing QR code transaction for user: {}", request.getUserId());

        // Validate QR code
        if (request.getQrCodeId() == null || request.getQrCodeData() == null) {
            throw new RuntimeException("Invalid QR code data");
        }

        // Set transaction type
        request.setTransactionType("QR_CODE");

        // Process as regular transaction
        return processTransaction(request);
    }

    /**
     * Verify transaction after scanning QR code
     */
    @Transactional
    public TransactionResponse verifyQRTransaction(String qrCodeId, String userId) {
        log.info("Verifying QR transaction: QR={}, User={}", qrCodeId, userId);

        // Find transaction by QR code
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        Transaction transaction = transactions.stream()
                .filter(t -> qrCodeId.equals(t.getQrCodeId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Transaction not found for QR code"));

        // Run post-transaction fraud check
        FraudDetectionResult fraudResult = fraudDetectionService.postTransactionCheck(transaction);

        // Update transaction
        transaction = transactionRepository.save(transaction);

        return buildResponse(transaction, fraudResult);
    }

    /**
     * Register a new user, or return existing user if email already taken
     */
    @Transactional
    public User registerUser(RegisterRequest request) {
        // If user already exists, return them instead of throwing an error
        return userRepository.findByEmail(request.getEmail()).orElseGet(() -> {
            User user = new User();
            user.setUserId("USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setPassword(request.getPassword()); // hash in production
            user.setTrustScore(100.0);
            user.setEnabled(true);
            user.setAccountLocked(false);
            user.setTotalTransactions(0);
            user.setFraudCount(0);
            User saved = userRepository.save(user);
            log.info("New user registered: {}", saved.getUserId());
            return saved;
        });
    }

    /**
     * Get transaction by ID
     */
    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
    }

    /**
     * Get user transactions
     */
    public List<Transaction> getUserTransactions(String userId) {
        return transactionRepository.findByUserIdOrderByTransactionTimeDesc(userId);
    }

    /**
     * Build transaction entity from request
     */
    private Transaction buildTransaction(TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(generateTransactionId());
        transaction.setUserId(request.getUserId());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setTransactionType(request.getTransactionType());
        transaction.setMerchantId(request.getMerchantId());
        transaction.setMerchantName(request.getMerchantName());
        transaction.setMerchantCategory(request.getMerchantCategory());
        transaction.setIpAddress(request.getIpAddress());
        transaction.setCountry(request.getCountry());
        transaction.setCity(request.getCity());
        transaction.setLatitude(request.getLatitude());
        transaction.setLongitude(request.getLongitude());
        transaction.setDeviceId(request.getDeviceId());
        transaction.setDeviceType(request.getDeviceType());
        transaction.setDeviceFingerprint(request.getDeviceFingerprint());
        transaction.setUserAgent(request.getUserAgent());
        transaction.setQrCodeId(request.getQrCodeId());
        transaction.setQrCodeData(request.getQrCodeData());
        transaction.setMetadata(request.getMetadata());
        transaction.setStatus("PENDING");
        transaction.setFraudStatus("UNKNOWN");
        transaction.setTransactionTime(LocalDateTime.now());

        return transaction;
    }

    /**
     * Enrich transaction with behavioral data
     */
    private void enrichTransactionWithBehavioralData(Transaction transaction) {
        // Calculate time since last transaction
        List<Transaction> recentTransactions = transactionRepository
                .findByUserIdOrderByTransactionTimeDesc(transaction.getUserId());

        if (!recentTransactions.isEmpty()) {
            Transaction lastTransaction = recentTransactions.get(0);
            long secondsSinceLast = ChronoUnit.SECONDS.between(
                    lastTransaction.getTransactionTime(),
                    transaction.getTransactionTime());
            transaction.setTimeSinceLastTransaction(secondsSinceLast);
        }

        // Calculate average transaction amount
        if (!recentTransactions.isEmpty()) {
            double avgAmount = recentTransactions.stream()
                    .filter(t -> "SAFE".equals(t.getFraudStatus()))
                    .mapToDouble(Transaction::getAmount)
                    .average()
                    .orElse(transaction.getAmount());
            transaction.setAvgTransactionAmount(avgAmount);
        }

        // Count recent transactions
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        long transLastHour = recentTransactions.stream()
                .filter(t -> t.getTransactionTime().isAfter(oneHourAgo))
                .count();
        long transLastDay = recentTransactions.stream()
                .filter(t -> t.getTransactionTime().isAfter(oneDayAgo))
                .count();

        transaction.setTransactionsInLastHour((int) transLastHour);
        transaction.setTransactionsInLastDay((int) transLastDay);
    }

    /**
     * Build transaction response
     */
    private TransactionResponse buildResponse(Transaction transaction, FraudDetectionResult fraudResult) {
        return TransactionResponse.builder()
                .transactionId(transaction.getTransactionId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .transactionType(transaction.getTransactionType())
                .status(transaction.getStatus())
                .fraudStatus(transaction.getFraudStatus())
                .fraudScore(fraudResult.getFraudScore())
                .fraudReason(fraudResult.getPrimaryReason())
                .approved("APPROVED".equals(transaction.getStatus()))
                .message(buildStatusMessage(transaction, fraudResult))
                .transactionTime(transaction.getTransactionTime())
                .fraudAnalysis(buildFraudAnalysis(fraudResult))
                .build();
    }

    /**
     * Build fraud analysis details
     */
    private TransactionResponse.FraudAnalysis buildFraudAnalysis(FraudDetectionResult result) {
        return TransactionResponse.FraudAnalysis.builder()
                .mlScore(result.getMlScore())
                .ruleBasedScore(result.getRuleBasedScore())
                .riskLevel(result.getRiskLevel())
                .triggeredRules(result.getTriggeredRules().toArray(new String[0]))
                .recommendation(result.getRecommendation())
                .behaviorAnalysis(buildBehaviorAnalysis(result))
                .build();
    }

    /**
     * Build behavior analysis details
     */
    private TransactionResponse.BehaviorAnalysis buildBehaviorAnalysis(FraudDetectionResult result) {
        return TransactionResponse.BehaviorAnalysis.builder()
                .unusualAmount(result.getUnusualAmount() != null && result.getUnusualAmount())
                .unusualTime(result.getUnusualTime() != null && result.getUnusualTime())
                .unusualLocation(result.getUnusualLocation() != null && result.getUnusualLocation())
                .unusualDevice(result.getUnusualDevice() != null && result.getUnusualDevice())
                .highVelocity(result.getHighVelocity() != null && result.getHighVelocity())
                .deviationFromNormal(result.getAmountDeviation())
                .build();
    }

    /**
     * Build status message
     */
    private String buildStatusMessage(Transaction transaction, FraudDetectionResult result) {
        switch (transaction.getStatus()) {
            case "APPROVED":
                return "Transaction approved successfully";
            case "DECLINED":
                return "Transaction declined - " + result.getPrimaryReason();
            case "REVIEW":
                return "Transaction flagged for manual review - " + result.getPrimaryReason();
            case "HOLD":
                return "Transaction on hold pending verification";
            case "BLOCKED":
                return "Transaction blocked - Fraud detected";
            default:
                return "Transaction status: " + transaction.getStatus();
        }
    }

    /**
     * Build declined response
     */
    private TransactionResponse buildDeclinedResponse(String reason, TransactionRequest request) {
        return TransactionResponse.builder()
                .transactionId(generateTransactionId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionType(request.getTransactionType())
                .status("DECLINED")
                .fraudStatus("FRAUD")
                .fraudScore(1.0)
                .fraudReason(reason)
                .approved(false)
                .message("Transaction declined - " + reason)
                .transactionTime(LocalDateTime.now())
                .build();
    }

    /**
     * Generate unique transaction ID
     */
    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}