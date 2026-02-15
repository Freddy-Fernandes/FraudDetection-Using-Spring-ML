FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY target/fraud-detection-system-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
