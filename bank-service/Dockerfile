# syntax=docker/dockerfile:1
# Build context là thư mục gốc (cần pom cha): docker build -f bank-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY bank-service/pom.xml bank-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY bank-service/src bank-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl bank-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/bank-service/target/bank-service-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
