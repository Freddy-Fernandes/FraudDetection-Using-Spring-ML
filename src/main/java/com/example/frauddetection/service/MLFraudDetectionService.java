package com.example.frauddetection.service;

import com.example.frauddetection.entity.Transaction;
import com.example.frauddetection.entity.UserBehavior;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MLFraudDetectionService {

    @Value("${ml.model.path:models/fraud_detection_model.zip}")
    private String modelPath;

    @Value("${ml.model.confidence.threshold:0.7}")
    private double confidenceThreshold;

    private MultiLayerNetwork model;
    private static final int INPUT_FEATURES = 20;
    private static final int HIDDEN_LAYER_1 = 64;
    private static final int HIDDEN_LAYER_2 = 32;
    private static final int OUTPUT_SIZE = 2; // Fraud or Not Fraud

    @PostConstruct
    public void init() {
        try {
            loadOrCreateModel();
            log.info("ML Fraud Detection Model initialized successfully");
        } catch (Exception e) {
            log.error("Error initializing ML model", e);
            createNewModel();
        }
    }

    private void loadOrCreateModel() throws IOException {
        File modelFile = new File(modelPath);
        if (modelFile.exists()) {
            log.info("Loading existing model from: {}", modelPath);
            model = ModelSerializer.restoreMultiLayerNetwork(modelFile);
        } else {
            log.info("Creating new model");
            createNewModel();
        }
    }

    private void createNewModel() {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(123)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(0.001))
                .list()
                .layer(0, new DenseLayer.Builder()
                        .nIn(INPUT_FEATURES)
                        .nOut(HIDDEN_LAYER_1)
                        .activation(Activation.RELU)
                        .build())
                .layer(1, new DenseLayer.Builder()
                        .nIn(HIDDEN_LAYER_1)
                        .nOut(HIDDEN_LAYER_2)
                        .activation(Activation.RELU)
                        .build())
                .layer(2, new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nIn(HIDDEN_LAYER_2)
                        .nOut(OUTPUT_SIZE)
                        .activation(Activation.SOFTMAX)
                        .build())
                .build();

        model = new MultiLayerNetwork(conf);
        model.init();

        log.info("New neural network model created");
    }

    /**
     * Predict fraud probability for a transaction
     */
    public double predictFraudProbability(Transaction transaction, UserBehavior userBehavior) {
        try {
            INDArray features = extractFeatures(transaction, userBehavior);
            INDArray output = model.output(features);

            // Get probability of fraud (index 1)
            double fraudProbability = output.getDouble(0, 1);

            log.debug("ML Fraud Probability for transaction {}: {}",
                    transaction.getTransactionId(), fraudProbability);

            return fraudProbability;
        } catch (Exception e) {
            log.error("Error predicting fraud probability", e);
            return 0.5; // Return neutral score on error
        }
    }

    /**
     * Extract features from transaction for ML model
     */
    private INDArray extractFeatures(Transaction transaction, UserBehavior behavior) {
        double[] features = new double[INPUT_FEATURES];
        int idx = 0;

        // Transaction amount features
        features[idx++] = normalizeAmount(transaction.getAmount());
        features[idx++] = behavior != null && behavior.getAvgTransactionAmount() != null
                ? transaction.getAmount() / behavior.getAvgTransactionAmount()
                : 1.0;

        // Time-based features
        LocalDateTime transTime = transaction.getTransactionTime();
        features[idx++] = transTime.getHour() / 24.0; // Hour of day (0-1)
        features[idx++] = transTime.getDayOfWeek().getValue() / 7.0; // Day of week (0-1)
        features[idx++] = transaction.getUnusualTime() != null && transaction.getUnusualTime() ? 1.0 : 0.0;

        // Velocity features
        features[idx++] = transaction.getTransactionsInLastHour() != null
                ? Math.min(transaction.getTransactionsInLastHour() / 10.0, 1.0)
                : 0.0;
        features[idx++] = transaction.getTransactionsInLastDay() != null
                ? Math.min(transaction.getTransactionsInLastDay() / 50.0, 1.0)
                : 0.0;
        features[idx++] = transaction.getVelocityScore() != null
                ? transaction.getVelocityScore()
                : 0.0;

        // Location features
        features[idx++] = transaction.getUnusualLocation() != null && transaction.getUnusualLocation() ? 1.0 : 0.0;
        features[idx++] = transaction.getLatitude() != null ? normalizeCoordinate(transaction.getLatitude()) : 0.0;
        features[idx++] = transaction.getLongitude() != null ? normalizeCoordinate(transaction.getLongitude()) : 0.0;

        // Device features
        features[idx++] = transaction.getUnusualDevice() != null && transaction.getUnusualDevice() ? 1.0 : 0.0;
        features[idx++] = "MOBILE".equals(transaction.getDeviceType()) ? 1.0 : 0.0;

        // Transaction type features
        features[idx++] = "QR_CODE".equals(transaction.getTransactionType()) ? 1.0 : 0.0;
        features[idx++] = "UPI".equals(transaction.getTransactionType()) ? 1.0 : 0.0;

        // User behavior features
        if (behavior != null) {
            features[idx++] = behavior.getConsistencyScore() != null ? behavior.getConsistencyScore() : 0.5;
            features[idx++] = behavior.getFailedAttempts() != null
                    ? Math.min(behavior.getFailedAttempts() / 10.0, 1.0)
                    : 0.0;
            features[idx++] = behavior.getChargebacks() != null
                    ? Math.min(behavior.getChargebacks() / 5.0, 1.0)
                    : 0.0;
        } else {
            features[idx++] = 0.5;
            features[idx++] = 0.0;
            features[idx++] = 0.0;
        }

        // Time since last transaction
        if (transaction.getTimeSinceLastTransaction() != null) {
            features[idx++] = Math.min(transaction.getTimeSinceLastTransaction() / 86400.0, 1.0);
        } else {
            features[idx++] = 1.0;
        }

        // Merchant category (simplified)
        features[idx++] = transaction.getMerchantCategory() != null ? 1.0 : 0.0;

        return Nd4j.create(features).reshape(1, INPUT_FEATURES);
    }

    /**
     * Train the model with new data
     */
    public void trainModel(List<Transaction> transactions, List<Boolean> labels) {
        try {
            if (transactions.size() != labels.size() || transactions.isEmpty()) {
                log.warn("Invalid training data size");
                return;
            }

            // Prepare training data
            INDArray input = Nd4j.zeros(transactions.size(), INPUT_FEATURES);
            INDArray output = Nd4j.zeros(transactions.size(), OUTPUT_SIZE);

            for (int i = 0; i < transactions.size(); i++) {
                INDArray features = extractFeatures(transactions.get(i), null);
                input.putRow(i, features);

                // One-hot encoding for output
                if (labels.get(i)) {
                    output.putScalar(new int[] { i, 1 }, 1.0); // Fraud
                } else {
                    output.putScalar(new int[] { i, 0 }, 1.0); // Not fraud
                }
            }

            DataSet dataSet = new DataSet(input, output);

            // Train the model
            for (int epoch = 0; epoch < 10; epoch++) {
                model.fit(dataSet);
            }

            log.info("Model trained with {} samples", transactions.size());

            // Save the updated model
            saveModel();
        } catch (Exception e) {
            log.error("Error training model", e);
        }
    }

    /**
     * Save the model to disk
     */
    public void saveModel() {
        try {
            File modelFile = new File(modelPath);
            modelFile.getParentFile().mkdirs();
            ModelSerializer.writeModel(model, modelFile, true);
            log.info("Model saved to: {}", modelPath);
        } catch (IOException e) {
            log.error("Error saving model", e);
        }
    }

    // Helper methods for normalization
    private double normalizeAmount(double amount) {
        // Log normalization for amount (assuming max ~100000)
        return Math.min(Math.log1p(amount) / Math.log(100000), 1.0);
    }

    private double normalizeCoordinate(double coordinate) {
        // Normalize latitude/longitude to 0-1 range
        return (coordinate + 180.0) / 360.0;
    }

    /**
     * Get model confidence level
     */
    public String getConfidenceLevel(double probability) {
        if (probability >= 0.9 || probability <= 0.1) {
            return "HIGH";
        } else if (probability >= 0.7 || probability <= 0.3) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}