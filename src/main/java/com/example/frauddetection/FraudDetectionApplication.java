package com.example.frauddetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class FraudDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionApplication.class, args);
        System.out.println("===========================================");
        System.out.println("Fraud Detection System Started Successfully");
        System.out.println("===========================================");
    }
}