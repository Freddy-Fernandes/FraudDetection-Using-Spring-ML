package com.example.frauddetection.controller;


import com.example.frauddetection.dto.RegisterRequest;
import com.example.frauddetection.dto.TransactionRequest;
import com.example.frauddetection.dto.TransactionResponse;
import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Process a new transaction
     * POST /api/transactions
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> processTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received transaction request for user: {}", request.getUserId());

        try {
            TransactionResponse response = transactionService.processTransaction(request);

            if (response.getApproved()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
            }
        } catch (Exception e) {
            log.error("Error processing transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * Process QR code transaction
     * POST /api/transactions/qr
     */
    @PostMapping("/qr")
    public ResponseEntity<TransactionResponse> processQRTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received QR transaction request for user: {}", request.getUserId());

        try {
            TransactionResponse response = transactionService.processQRTransaction(request);

            if (response.getApproved()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
            }
        } catch (Exception e) {
            log.error("Error processing QR transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * Verify QR transaction after scanning
     * POST /api/transactions/qr/verify
     */
    @PostMapping("/qr/verify")
    public ResponseEntity<TransactionResponse> verifyQRTransaction(
            @RequestParam String qrCodeId,
            @RequestParam String userId) {
        log.info("Verifying QR transaction: {}", qrCodeId);

        try {
            TransactionResponse response = transactionService.verifyQRTransaction(qrCodeId, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error verifying QR transaction", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildErrorResponse(e.getMessage()));
        }
    }

    /**
     * Get transaction by ID
     * GET /api/transactions/{transactionId}
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String transactionId) {
        log.info("Fetching transaction: {}", transactionId);

        try {
            Transaction transaction = transactionService.getTransaction(transactionId);
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            log.error("Error fetching transaction", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get user transactions
     * GET /api/transactions/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transaction>> getUserTransactions(@PathVariable String userId) {
        log.info("Fetching transactions for user: {}", userId);

        try {
            List<Transaction> transactions = transactionService.getUserTransactions(userId);
            return ResponseEntity.ok(transactions);
        } catch (Exception e) {
            log.error("Error fetching user transactions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            transactionService.registerUser(request);
            return ResponseEntity.ok("User registered successfully");
        } catch (Exception e) {
            log.error("Error registering user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
    /**
     * Build error response
     */
    private TransactionResponse buildErrorResponse(String message) {
        return TransactionResponse.builder()
                .status("ERROR")
                .approved(false)
                .message(message)
                .build();
    }
}