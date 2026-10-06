# =====================================================================
# Multi-stage build. The JDK and the Maven repository stay in the build stage,
# so the shipped image contains only a JRE and the application jar.
# =====================================================================

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# Copy only the build descriptors first. Docker caches this layer, so editing
# application code does not re-download every dependency - only a change to
# pom.xml does.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
# Tests are not run here: they need a Docker daemon for Testcontainers, which is
# not available inside a build container. CI runs `mvnw verify` separately.
RUN ./mvnw -B -q -DskipTests package \
    && mv target/*.jar /build/application.jar

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre

# wget is used by the compose healthcheck; the JRE image does not ship it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

# Never run the application as root.
RUN groupadd --system --gid 1001 hrsolution \
    && useradd --system --uid 1001 --gid hrsolution --create-home hrsolution

WORKDIR /app
COPY --from=build --chown=hrsolution:hrsolution /build/application.jar ./application.jar

# Directory for locally stored uploads (worker documents, logos). Mount a volume
# over this in any real deployment - a container filesystem is disposable.
RUN mkdir -p /app/uploads && chown hrsolution:hrsolution /app/uploads

USER hrsolution

EXPOSE 8080

ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# Exec form via sh so JAVA_OPTS is word-split; ExitOnOutOfMemoryError makes the
# orchestrator restart a wedged JVM instead of leaving it half-alive.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
