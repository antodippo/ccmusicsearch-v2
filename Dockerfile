# Build
FROM eclipse-temurin:17.0.19_10-jdk-noble AS builder
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradlew /app/
COPY gradle /app/gradle

RUN chmod +x /app/gradlew
RUN ./gradlew --version

COPY src /app/src
RUN ./gradlew test --no-daemon
RUN ./gradlew build --no-daemon

# Run
FROM eclipse-temurin:17.0.19_10-jre-noble
WORKDIR /app

COPY --from=builder /app/build/libs/ccmusicsearch-0.0.1.jar /app/ccmusicsearch.jar
COPY .env.dist /app/.env

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "ccmusicsearch.jar"]
