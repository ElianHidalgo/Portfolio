# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached between source changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Tests are run locally/CI (they need Docker for Testcontainers), not here.
COPY src ./src
RUN mvn -B -q package -DskipTests && cp target/portfolio-*.jar /workspace/app.jar

# --- Runtime stage ---
FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
