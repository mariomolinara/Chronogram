# --- Stage 1: build the executable jar ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first for faster incremental builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Stage 2: minimal runtime image ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/target/chronogram.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
