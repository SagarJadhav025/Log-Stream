# ════════════════════════════════════════════════════════════════════════
# Stage 1 — Build the Spring Boot fat-JAR with Maven
# ════════════════════════════════════════════════════════════════════════
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Cache dependency layer separately (invalidated only when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy sources and build — skip tests (run in CI before Docker build)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ════════════════════════════════════════════════════════════════════════
# Stage 2 — Minimal JRE runtime image
# ════════════════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="LogStream Backend" \
      org.opencontainers.image.description="High-throughput log ingestion, search & alerting (gRPC + REST + WebSocket)" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app

# Non-root user for security hardening
RUN addgroup -S logstream && adduser -S logstream -G logstream
USER logstream

# Copy the fat JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Lucene on-disk index volume mount point
VOLUME ["/app/lucene-index"]

# REST (HTTP) + gRPC ports
EXPOSE 8080 9090

# JVM flags: ZGC with generational mode, pre-sized heap, virtual threads enabled
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-Xms512m", \
  "-Xmx2g", \
  "-XX:MaxMetaspaceSize=256m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dlogstream.index-dir=/app/lucene-index", \
  "-jar", "app.jar"]
