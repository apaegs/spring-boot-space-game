# Multi-stage build for spring-boot-space-game.
#
# Stage 1 builds the deployable jar — which includes both the Java backend
# AND the Vite-built SPA, baked into src/main/resources/static/ by
# frontend-maven-plugin (see pom.xml). The plugin downloads its own Node and
# pnpm into target/, so the build image only needs a JDK.
#
# Stage 2 is the slim runtime: just a JRE and the jar.

# --- Stage 1: build ------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Dependency-warmup layer.
#
# Docker caches each instruction's output by hashing the inputs. Copying
# the pom + wrapper alone and running dependency:go-offline before any
# source is copied means this slow step (hundreds of MB of Maven Central
# downloads on a cold build) gets cached as a layer keyed on the pom.
# Subsequent builds that only change Java/TS sources skip straight past
# it. Without this split, every source edit would re-trigger the full
# dependency download — the single longest step in the build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copy backend source.
COPY src/ src/

# Copy frontend source. frontend-maven-plugin reads frontend/package.json
# to invoke pnpm; the Vite output lands in frontend/dist/ and gets copied
# into target/classes/static/ as part of the package phase.
COPY frontend/ frontend/

# Build the jar. Skip tests — CI is the gate for tests, the Docker build
# should be deterministic and fast. Don't skip the frontend build; that's
# the whole point of bundling here.
RUN ./mvnw -B -q clean package -DskipTests


# --- Stage 2: runtime ----------------------------------------------------
FROM eclipse-temurin:25-jre

WORKDIR /app

# Run as a non-root user. Eclipse Temurin doesn't ship one, so we make our
# own and chown the app dir to it.
RUN groupadd --system spring && useradd --system --gid spring spring \
    && chown spring:spring /app
USER spring:spring

COPY --from=builder --chown=spring:spring /build/target/*.jar app.jar

# Spring's docker-compose autodetect is dev-only; in prod we run our own
# compose with a real Postgres, so the autodetect would just spin in
# circles looking for a compose.yaml that isn't there.
ENV SPRING_DOCKER_COMPOSE_ENABLED=false

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
