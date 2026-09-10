# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM gradle:9.7.1-jdk21 AS build

WORKDIR /workspace

COPY build.gradle.kts settings.gradle.kts ./

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean bootJar --no-daemon


# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home --uid 1001 appuser

COPY --from=build /workspace/build/libs/resource-booking-system.jar app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]