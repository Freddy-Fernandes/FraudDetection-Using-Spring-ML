package com.example.frauddetection.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String password;

    private String name;

    @Column(nullable = false)
    private LocalDateTime registrationDate;

    private String kycStatus; // VERIFIED, PENDING, NOT_VERIFIED

    private Double trustScore = 100.0; // Initial trust score

    private Integer totalTransactions = 0;

    private Double totalTransactionAmount = 0.0;

    private Integer fraudCount = 0;

    private Boolean accountLocked = false;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        registrationDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
