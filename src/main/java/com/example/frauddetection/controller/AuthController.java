package com.example.frauddetection.controller;

import com.example.frauddetection.entity.User;
import com.example.frauddetection.repository.UserRepository;
import com.example.frauddetection.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Admin login endpoint
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for: {}", request.getEmail());

        try {
            User user = userRepository.findByEmail(request.getEmail())
                    .orElse(null);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid email or password"));
            }

            // Check password
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Invalid email or password"));
            }

            // Check if account is locked
            if (user.getAccountLocked()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("success", false, "message", "Account is locked"));
            }

            // Determine role - check if admin
            String role = isAdminEmail(user.getEmail()) ? "ADMIN" : "USER";

            // Generate JWT token
            String token = jwtUtil.generateToken(user.getUserId(), user.getEmail(), role);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("token", token);
            response.put("userId", user.getUserId());
            response.put("email", user.getEmail());
            response.put("name", user.getName());
            response.put("role", role);
            response.put("trustScore", user.getTrustScore());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Login error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Login failed: " + e.getMessage()));
        }
    }

    /**
     * Register endpoint with password hashing
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for: {}", request.getEmail());

        try {
            // Check if user exists
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false, "message", "Email already registered"));
            }

            // Create new user
            User user = new User();
            user.setUserId("USR-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            user.setEmail(request.getEmail());
            user.setPhoneNumber(request.getPhoneNumber());
            user.setName(request.getName());
            user.setPassword(passwordEncoder.encode(request.getPassword())); // Hash password
            user.setTrustScore(100.0);
            user.setEnabled(true);
            user.setAccountLocked(false);
            user.setTotalTransactions(0);
            user.setFraudCount(0);

            User savedUser = userRepository.save(user);

// Determine role
String role = isAdminEmail(savedUser.getEmail()) ? "ADMIN" : "USER";

// Generate token
            String token = jwtUtil.generateToken(savedUser.getUserId(), savedUser.getEmail(), role);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Registration successful");
            response.put("token", token);
            response.put("userId", savedUser.getUserId());
            response.put("email", savedUser.getEmail());
            response.put("name", savedUser.getName());
            response.put("role", role);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Registration error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Registration failed: " + e.getMessage()));
        }
    }

    /**
     * Check if email is admin
     * Add your admin emails here
     */
    private boolean isAdminEmail(String email) {
        // Add your admin emails here
        return email.equals("admin@frauddetection.com") ||
                email.equals("freddyfernandes506@gmail.com") ||
                email.endsWith("@admin.frauddetection.com");
    }

    // DTOs
    @lombok.Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @lombok.Data
    public static class RegisterRequest {
        private String email;
        private String password;
        private String name;
        private String phoneNumber;
    }
}