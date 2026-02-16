package com.example.frauddetection.controller;

import com.example.frauddetection.entity.User;
import com.example.frauddetection.repository.UserRepository;
import com.example.frauddetection.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserRepository userRepository;
    private final UserBehaviorService behaviorService;
    
    /**
     * Register a new user OR return existing user info
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@RequestBody UserRegistrationRequest request) {
        log.info("Registration request for email: {}", request.getEmail());
        
        try {
            // Check if user already exists by email
            if (userRepository.existsByEmail(request.getEmail())) {
                log.info("User already exists with email: {}", request.getEmail());
                
                // Get existing user
                User existingUser = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));
                
                // Return existing user info with message
                UserResponse response = UserResponse.builder()
                    .success(true)
                    .message("User already registered. Returning existing user details.")
                    .userId(existingUser.getUserId())
                    .email(existingUser.getEmail())
                    .name(existingUser.getName())
                    .phoneNumber(existingUser.getPhoneNumber())
                    .trustScore(existingUser.getTrustScore())
                    .accountLocked(existingUser.getAccountLocked())
                    .enabled(existingUser.getEnabled())
                    .totalTransactions(existingUser.getTotalTransactions())
                    .fraudCount(existingUser.getFraudCount())
                    .createdAt(existingUser.getCreatedAt())
                    .build();
                
                return ResponseEntity.ok(response);
            }
            
            // Check if user exists by phone number
            if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                log.info("User already exists with phone: {}", request.getPhoneNumber());
                
                User existingUser = userRepository.findByPhoneNumber(request.getPhoneNumber())
                    .orElseThrow(() -> new RuntimeException("User not found"));
                
                UserResponse response = UserResponse.builder()
                    .success(true)
                    .message("User already registered with this phone number. Returning existing user details.")
                    .userId(existingUser.getUserId())
                    .email(existingUser.getEmail())
                    .name(existingUser.getName())
                    .phoneNumber(existingUser.getPhoneNumber())
                    .trustScore(existingUser.getTrustScore())
                    .accountLocked(existingUser.getAccountLocked())
                    .enabled(existingUser.getEnabled())
                    .totalTransactions(existingUser.getTotalTransactions())
                    .fraudCount(existingUser.getFraudCount())
                    .createdAt(existingUser.getCreatedAt())
                    .build();
                
                return ResponseEntity.ok(response);
            }
            
            // Create new user
            User user = new User();
            user.setUserId("USR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setName(request.getName());
            user.setPassword(request.getPassword()); // In production, hash this!
            user.setTrustScore(100.0);
            user.setEnabled(true);
            user.setAccountLocked(false);
            user.setTotalTransactions(0);
            user.setFraudCount(0);
            
            User savedUser = userRepository.save(user);
            
            // Initialize behavior profile
            behaviorService.getUserBehavior(savedUser.getUserId());
            
            log.info("New user registered successfully: {}", savedUser.getUserId());
            
            UserResponse response = UserResponse.builder()
                .success(true)
                .message("User registered successfully!")
                .userId(savedUser.getUserId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .phoneNumber(savedUser.getPhoneNumber())
                .trustScore(savedUser.getTrustScore())
                .accountLocked(savedUser.getAccountLocked())
                .enabled(savedUser.getEnabled())
                .totalTransactions(savedUser.getTotalTransactions())
                .fraudCount(savedUser.getFraudCount())
                .createdAt(savedUser.getCreatedAt())
                .build();
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error during registration", e);
            
            UserResponse errorResponse = UserResponse.builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Get user by userId OR email
     * GET /api/users/{identifier}
     */
    @GetMapping("/{identifier}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String identifier) {
        log.info("Fetching user: {}", identifier);
        
        try {
            User user = null;
            
            // Try to find by userId first
            if (identifier.startsWith("USR-")) {
                user = userRepository.findByUserId(identifier).orElse(null);
            }
            
            // If not found, try by email
            if (user == null && identifier.contains("@")) {
                user = userRepository.findByEmail(identifier).orElse(null);
            }
            
            // If still not found, try by phone
            if (user == null) {
                user = userRepository.findByPhoneNumber(identifier).orElse(null);
            }
            
            if (user == null) {
                UserResponse response = UserResponse.builder()
                    .success(false)
                    .message("User not found")
                    .build();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            UserResponse response = UserResponse.builder()
                .success(true)
                .message("User found")
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .trustScore(user.getTrustScore())
                .accountLocked(user.getAccountLocked())
                .enabled(user.getEnabled())
                .totalTransactions(user.getTotalTransactions())
                .fraudCount(user.getFraudCount())
                .createdAt(user.getCreatedAt())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error fetching user", e);
            
            UserResponse errorResponse = UserResponse.builder()
                .success(false)
                .message("Error: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Login - Get user by email and password
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        
        try {
            User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);
            
            if (user == null) {
                UserResponse response = UserResponse.builder()
                    .success(false)
                    .message("User not found with this email")
                    .build();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            // Check password (in production, use BCrypt)
            if (!user.getPassword().equals(request.getPassword())) {
                UserResponse response = UserResponse.builder()
                    .success(false)
                    .message("Invalid password")
                    .build();
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
            // Check if account is locked
            if (user.getAccountLocked()) {
                UserResponse response = UserResponse.builder()
                    .success(false)
                    .message("Account is locked due to suspicious activity")
                    .userId(user.getUserId())
                    .email(user.getEmail())
                    .accountLocked(true)
                    .build();
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            
            UserResponse response = UserResponse.builder()
                .success(true)
                .message("Login successful")
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .phoneNumber(user.getPhoneNumber())
                .trustScore(user.getTrustScore())
                .accountLocked(user.getAccountLocked())
                .enabled(user.getEnabled())
                .totalTransactions(user.getTotalTransactions())
                .fraudCount(user.getFraudCount())
                .createdAt(user.getCreatedAt())
                .build();
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during login", e);
            
            UserResponse errorResponse = UserResponse.builder()
                .success(false)
                .message("Login failed: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Update user trust score manually
     * PUT /api/users/{userId}/trust-score
     */
    @PutMapping("/{userId}/trust-score")
    public ResponseEntity<Map<String, Object>> updateTrustScore(
            @PathVariable String userId,
            @RequestParam Double trustScore) {
        log.info("Updating trust score for user: {}", userId);
        
        try {
            User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setTrustScore(Math.max(0, Math.min(100, trustScore)));
            User updated = userRepository.save(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Trust score updated");
            response.put("userId", updated.getUserId());
            response.put("trustScore", updated.getTrustScore());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating trust score", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Lock/unlock user account
     * PUT /api/users/{userId}/lock
     */
    @PutMapping("/{userId}/lock")
    public ResponseEntity<Map<String, Object>> lockAccount(
            @PathVariable String userId,
            @RequestParam Boolean locked) {
        log.info("Updating lock status for user: {}", userId);
        
        try {
            User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            user.setAccountLocked(locked);
            user.setEnabled(!locked);
            User updated = userRepository.save(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", locked ? "Account locked" : "Account unlocked");
            response.put("userId", updated.getUserId());
            response.put("accountLocked", updated.getAccountLocked());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error locking/unlocking account", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Update user behavior profile
     * POST /api/users/{userId}/update-behavior
     */
    @PostMapping("/{userId}/update-behavior")
    public ResponseEntity<Map<String, String>> updateBehavior(@PathVariable String userId) {
        log.info("Manually updating behavior for user: {}", userId);
        
        try {
            behaviorService.updateBehavior(userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("success", "true");
            response.put("message", "Behavior profile updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating behavior", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("success", "false");
            response.put("message", "Error updating behavior: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    // ===== DTOs =====
    
    @lombok.Data
    public static class UserRegistrationRequest {
        private String email;
        private String phoneNumber;
        private String name;
        private String password;
    }
    
    @lombok.Data
    public static class LoginRequest {
        private String email;
        private String password;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class UserResponse {
        private Boolean success;
        private String message;
        private String userId;
        private String email;
        private String name;
        private String phoneNumber;
        private Double trustScore;
        private Boolean accountLocked;
        private Boolean enabled;
        private Integer totalTransactions;
        private Integer fraudCount;
        private LocalDateTime createdAt;
    }
}