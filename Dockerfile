# syntax=docker/dockerfile:1@sha256:ecfaec9ed6d810b56388c508f4121597bfbba70d41a6dfeee4d8cad5f295fc32
#
# Single-process ReleaseFlow image used by docker-compose.demo.yml and CI.
# Orchestration, TLS termination, and secret management remain deployment
# responsibilities. The health check uses the public GET /api/status endpoint
# until a private management port exists.

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk@sha256:1f79c73404fb0cccf9a3459eda22892f368d994b1028d6fb1ae871c1f49749a6 AS build
WORKDIR /app

# Resolve Maven dependencies before copying sources so source-only changes
# reuse this layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B --no-transfer-progress dependency:go-offline

# frontend-maven-plugin runs the Tailwind build during `package`.
COPY package.json package-lock.json postcss.config.js ./
COPY src/ src/
RUN ./mvnw -B --no-transfer-progress clean package -DskipTests -Prelease

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-ubi10-minimal@sha256:f96427483628dfa8d8c662f3c02958960e5f0d75adf8b4f89fcbb2f1a6b5ab5a AS runtime
WORKDIR /app

COPY --from=build --chown=65534:65534 /app/target/releaseflow.jar app.jar
USER 65534:65534

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["curl", "--fail", "--silent", "--show-error", "--output", "/dev/null", "http://127.0.0.1:8080/api/status"]
ENTRYPOINT ["java", "-jar", "app.jar"]
