package com.example.frauddetection.repository;

import com.example.frauddetection.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByUserId(String userId);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByPhoneNumber(String phoneNumber);
    
    boolean existsByUserId(String userId);
    
    boolean existsByEmail(String email);
    
    boolean existsByPhoneNumber(String phoneNumber);
}