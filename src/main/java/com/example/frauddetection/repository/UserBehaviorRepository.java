package com.example.frauddetection.repository;

import com.example.frauddetection.entity.UserBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehavior, Long> {
    Optional<UserBehavior> findByUserId(String userId);

    boolean existsByUserId(String userId);
}