# syntax=docker/dockerfile:1
# Build context là thư mục gốc (cần pom cha): docker build -f notification-service/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY bank-service/pom.xml bank-service/
COPY payment-service/pom.xml payment-service/
COPY notification-service/pom.xml notification-service/
COPY notification-service/src notification-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-*.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
