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
# Tuned for small free instances (Render: 512 MB, 0.1 CPU): one-thread GC, bounded heap,
# C1-only JIT for a faster start, smaller thread stacks, and a clean exit instead of a
# half-dead JVM if memory runs out.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
