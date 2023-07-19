FROM openjdk:17-jdk-slim
WORKDIR /app
COPY build/libs/ccmusicsearch-0.0.1.jar /app/ccmusicsearch.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ccmusicsearch.jar"]
