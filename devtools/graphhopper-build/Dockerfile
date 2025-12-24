# Multi-stage build that produces a shaded GraphHopper Matrix server JAR
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy Maven descriptors first for dependency caching
COPY pom.xml ./pom.xml
COPY matrix-extension/pom.xml matrix-extension/pom.xml

# Prime the local Maven repository offline for reproducible builds
RUN mvn -f matrix-extension/pom.xml dependency:go-offline

# Copy the application sources and build the shaded JAR
COPY matrix-extension/src/ matrix-extension/src/
RUN mvn -f matrix-extension/pom.xml clean package -DskipTests

# Runtime image keeps only the runtime dependencies and configuration
FROM eclipse-temurin:21-jre
WORKDIR /app

# Bring in the shaded application JAR and configuration assets
COPY --from=builder /build/matrix-extension/target/matrix-server.jar /app/app.jar
COPY devtools/graphhopper-build/config.yml /app/config.yml
COPY devtools/graphhopper-build/dynop-truck.json /app/dynop-truck.json

VOLUME /app/osm
VOLUME /app/graph-cache

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar app.jar server config.yml"]
