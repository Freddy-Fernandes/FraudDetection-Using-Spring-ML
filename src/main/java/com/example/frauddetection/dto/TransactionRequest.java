package com.example.frauddetection.dto;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Double amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Transaction type is required")
    private String transactionType; // QR_CODE, UPI, CARD, WALLET

    private String merchantId;

    private String merchantName;

    private String merchantCategory;

    // Location data
    private String ipAddress;

    private String country;

    private String city;

    private Double latitude;

    private Double longitude;

    // Device information
    @NotBlank(message = "Device ID is required")
    private String deviceId;

    private String deviceType;

    private String deviceFingerprint;

    private String userAgent;

    // QR Code specific
    private String qrCodeId;

    private String qrCodeData;

    // Additional metadata
    private String metadata;
}