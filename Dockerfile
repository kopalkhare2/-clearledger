# Stage 1: Build JAR using Maven with Eclipse Temurin JDK 21
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Lightweight runtime image with Eclipse Temurin JRE 21 Alpine
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/clearledger-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
