FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 🔴 ADD THIS LINE (important)
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests
