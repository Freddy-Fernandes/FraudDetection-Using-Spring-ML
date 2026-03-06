# ── Stage 1: Build the JAR using Maven ──────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml first (cached layer - only re-downloads dependencies if pom changes)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run the JAR ─────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar



RUN apk add --no-cache python3 py3-pip && \
    pip3 install --break-system-packages \
        numpy==1.24.3 \
        pandas==2.0.3 \
        scikit-learn==1.3.0 \
        joblib==1.3.2

# Copy ML model files
COPY model/fraud_model_best.pkl /app/
COPY model/fraud_scaler.pkl /app/
COPY model/fraud_features.pkl /app/
COPY model/predict_fraud.py /app/
RUN chmod +x /app/predict_fraud.py

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]