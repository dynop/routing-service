# dynop — GraphHopper Multi-Stage Docker Build

## GitHub Copilot Implementation Prompt (Final v3.0)

You are GitHub Copilot acting as a **Senior DevOps Engineer**.  
Your task: **generate a complete, production-grade multi-stage Docker build system** for GraphHopper.  
This build must require **zero host dependencies** (only Docker).  
All building happens *inside* Docker.

This prompt is the **single authoritative specification**.  
Follow it exactly. No deviations allowed.

---

## 1. Directory Structure

Create the following under:

```
devtools/graphhopper-build/
```

Directory contents:

```
/devtools/graphhopper-build/
├── Dockerfile
├── config.yml
└── README.md
```

Not allowed:

- ❌ No shell scripts
- ❌ No external build tools
- ❌ No src folder
- ❌ No host JVM/Maven installation

All build logic MUST be inside Dockerfile.

---

## 2. Dockerfile Requirements (Multi-Stage Build)

### Stage 1 — Builder (JDK 21 + Maven)

**Base image:**

```dockerfile
maven:3.9-eclipse-temurin-21
```

**Build ARGs (must be implemented):**

```dockerfile
ARG GH_VERSION=11.0
```

**Mandatory build steps:**

1. `git clone https://github.com/graphhopper/graphhopper.git` into `/build/graphhopper`

2. Checkout version:
    ```bash
    git checkout ${GH_VERSION}
    ```

3. **Layer-optimized dependency caching:**
    - Copy only `pom.xml` first
    - Run:
      ```bash
      mvn -f pom.xml dependency:go-offline
      ```
    This ensures Maven dependencies are cached even if source changes.

4. Copy full GraphHopper source into builder stage.

5. Build GraphHopper:
    ```bash
    mvn -f pom.xml clean package -DskipTests
    ```

6. After build, the JAR must exist at:
    ```
    /build/graphhopper/web/target/graphhopper-web-*.jar
    ```
    This jar will be copied to Stage 2.

---

### Stage 2 — Runtime (JRE 21)

**Base image:**

```dockerfile
eclipse-temurin:21-jre
```

**Runtime Requirements:**

- Create directories:
  ```bash
  /app
  /data
  /data/graph-cache
  ```

- Create non-root user:
  ```bash
  useradd -u 1000 -m graphhopper
  ```

- Copy from builder stage:
  - `graphhopper-web-*.jar → /app/graphhopper.jar`
  - `config.yml → /app/config.yml`

- Set ownership of `/app` and `/data` to `graphhopper:graphhopper`

- Default environment variable:
  ```dockerfile
  ENV JAVA_OPTS="-Xms1g -Xmx1g"
  ```
  (User may override via `docker run -e JAVA_OPTS=`.)

- Expose port:
  ```dockerfile
  EXPOSE 8989
  ```

- Entrypoint:
  ```dockerfile
  ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/graphhopper.jar server /app/config.yml"]
  ```

- Switch user:
  ```dockerfile
  USER graphhopper
  ```

---

## 3. Config Template (config.yml)

Generate a professional truck-optimized production config:

```yaml
graphhopper:
  datareader.file: /data/osm.pbf
  graph.location: /data/graph-cache

  profiles:
     - name: truck
        vehicle: truck
        weighting: fastest
        turn_costs: true

  profiles_ch:
     - profile: truck

  web:
     enable_js: false
     cors:
        enabled: true
```

---

## 4. Documentation (README.md)

Must explain:

### How to build the image

```bash
docker build \
  --build-arg GH_VERSION=11.0 \
  -t dynop-graphhopper:11.0 \
  .
```

### How to run the container

```bash
docker run -d \
  --name graphhopper-eu \
  -p 8989:8989 \
  -e JAVA_OPTS="-Xms8g -Xmx8g" \
  -v $(pwd)/data:/data \
  dynop-graphhopper:11.0
```

Where:

- `$(pwd)/data/osm.pbf` must contain your Europe OSM file
- `$(pwd)/data/graph-cache` will store CH graph cache

### Notes for developers

- First run is slow (imports OSM & builds CH graph)
- Subsequent runs are fast (cache reused)
- Zero host dependencies (no JDK, Maven, or GraphHopper install needed)
- Works identically on Linux, macOS, Windows, CI pipelines

---

## 5. Acceptance Criteria — Copilot MUST deliver:

- ✔ Fully working multi-stage Dockerfile
- ✔ Valid config.yml truck routing profile
- ✔ Complete README.md documentation
- ✔ No shell scripts, no external build tools
- ✔ No dependence on host JVM/Maven
- ✔ Entire build happens inside Docker
- ✔ Uses Maven dependency caching layers
- ✔ Production-grade best practices