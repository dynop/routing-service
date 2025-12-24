# dynop GraphHopper Multi-Stage Docker Build
# Production-grade, zero-dependency build system for GraphHopper
# Requires only Docker - no host JVM/Maven installation needed

# =============================================================================
# Stage 1: Builder (JDK 21 + Maven)
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

# Build arguments
ARG GH_VERSION=11.0

# Set working directory
WORKDIR /build

# Clone GraphHopper repository
RUN git clone https://github.com/graphhopper/graphhopper.git graphhopper

# Checkout specified version
WORKDIR /build/graphhopper
RUN git checkout ${GH_VERSION}

# Layer optimization: Copy only pom.xml first for dependency caching
# This ensures Maven dependencies are cached even if source code changes
COPY --from=maven:3.9-eclipse-temurin-21 /usr/share/maven /usr/share/maven
RUN mvn -f pom.xml dependency:go-offline -B

# Build GraphHopper (skip tests for faster builds)
RUN mvn -f pom.xml clean package -DskipTests -B

# Verify JAR exists
RUN ls -lh /build/graphhopper/web/target/graphhopper-web-*.jar

# =============================================================================
# Stage 2: Runtime (JRE 21)
# =============================================================================
FROM eclipse-temurin:21-jre

# Metadata
LABEL maintainer="dynop"
LABEL description="GraphHopper routing engine with truck profile"
LABEL version="1.0"

# Create directories
RUN mkdir -p /app /data /data/graph-cache

# Create non-root user
RUN groupadd -g 1001 graphhopper && \
    useradd -u 1001 -g 1001 -m -s /bin/bash graphhopper

# Copy GraphHopper JAR from builder stage
COPY --from=builder /build/graphhopper/web/target/graphhopper-web-*.jar /app/graphhopper.jar

# Copy configuration
COPY config.yml /app/config.yml

# Set ownership
RUN chown -R graphhopper:graphhopper /app /data

# Default JVM settings (can be overridden)
ENV JAVA_OPTS="-Xms1g -Xmx1g"

# Expose GraphHopper port
EXPOSE 8989

# Set working directory
WORKDIR /app

# Switch to non-root user
USER graphhopper

# Entrypoint with dynamic JAVA_OPTS support
ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/graphhopper.jar server /app/config.yml"]
