# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy dependency descriptor first for Docker layer caching
COPY pom.xml .

# Cache Maven dependencies between builds
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests dependency:go-offline

# Copy source code
COPY src ./src

# Build application
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run application as non-root user
RUN useradd --system --create-home --uid 1001 appuser

# Copy only the packaged application
COPY --from=build /workspace/target/resource-booking-system.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]