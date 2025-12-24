# dynop GraphHopper Docker Build

Production-grade multi-stage Docker build for GraphHopper routing engine with truck profile optimization.

## Features

✅ **Zero host dependencies** - Only Docker required  
✅ **Multi-stage build** - Optimized image size (~300MB runtime vs ~1GB+ builder)  
✅ **Maven layer caching** - Fast rebuilds when dependencies don't change  
✅ **Production-ready** - Non-root user, configurable JVM options  
✅ **Truck routing optimized** - Pre-configured for commercial vehicle routing  
✅ **Portable** - Works identically on Linux, macOS, Windows, CI/CD

---

## Quick Start

### 1. Build the Image

Build GraphHopper version 11.0:

```bash
cd devtools/graphhopper-build

docker build \
  --build-arg GH_VERSION=11.0 \
  -t dynop-graphhopper:11.0 \
  .
```

Build GraphHopper version 11.0 (default):

```bash
docker build \
  -t dynop-graphhopper:11.0 \
  .
```

**Build time:** ~5-10 minutes (depending on network speed and CPU)

---

### 2. Prepare OSM Data

Ensure your OSM data is ready:

```bash
# Your OSM file should be at:
# /home/dynop/Desktop/dynop-Project/GEO/osm-data/europe/europe-latest.osm.pbf

# Create graph cache directory (will be populated on first run)
mkdir -p ~/graphhopper-cache
```

---

### 3. Run the Container

Basic run with 8GB RAM allocation:

```bash
docker run -d \
  --name graphhopper-eu \
  -p 8989:8989 \
  -e JAVA_OPTS="-Xms8g -Xmx8g" \
  -v /home/dynop/Desktop/dynop-Project/GEO/osm-data/europe:/data:ro \
  -v ~/graphhopper-cache:/data/graph-cache \
  dynop-graphhopper:11.0
```

**What happens:**
- First run: Imports OSM data and builds Contraction Hierarchies (CH) graph (~30-60 min for Europe)
- Subsequent runs: Reuses cached graph (starts in ~30 seconds)

---

## Usage Examples

### View Logs

```bash
docker logs -f graphhopper-eu
```

### Stop Container

```bash
docker stop graphhopper-eu
```

### Start Existing Container

```bash
docker start graphhopper-eu
```

### Remove Container

```bash
docker rm -f graphhopper-eu
```

### Test API

Once running, test the routing API:

```bash
# Health check
curl http://localhost:8989/health

# Sample truck route (Berlin to Munich)
curl "http://localhost:8989/route?point=52.520008,13.404954&point=48.135125,11.581981&vehicle=truck&locale=en"
```

---

## Configuration

### JVM Memory Settings

Adjust based on your data size:

| Region        | OSM Size | Recommended RAM | JAVA_OPTS            |
|---------------|----------|-----------------|----------------------|
| Small country | < 500MB  | 2-4GB           | `-Xms2g -Xmx4g`      |
| Large country | 1-3GB    | 4-8GB           | `-Xms4g -Xmx8g`      |
| Europe        | ~30GB    | 16-32GB         | `-Xms16g -Xmx32g`    |
| Planet        | ~70GB    | 64GB+           | `-Xms32g -Xmx64g`    |

### Custom Configuration

To use a custom `config.yml`:

```bash
docker run -d \
  --name graphhopper-eu \
  -p 8989:8989 \
  -e JAVA_OPTS="-Xms8g -Xmx8g" \
  -v /path/to/osm-data:/data:ro \
  -v /path/to/cache:/data/graph-cache \
  -v /path/to/custom-config.yml:/app/config.yml:ro \
  dynop-graphhopper:11.0
```

---

## Architecture

### Multi-Stage Build

**Stage 1: Builder** (maven:3.9-eclipse-temurin-21)
- Clones GraphHopper from GitHub
- Checks out specified version
- Downloads Maven dependencies (cached layer)
- Compiles GraphHopper from source
- Produces `graphhopper-web-*.jar`

**Stage 2: Runtime** (eclipse-temurin:21-jre)
- Minimal JRE 21 base image
- Copies only the compiled JAR
- Runs as non-root user (`graphhopper:1000`)
- Exposes port 8989

### Directory Structure

```
Container paths:
/app/
  ├── graphhopper.jar    # Compiled GraphHopper application
  └── config.yml         # Configuration file

/data/
  ├── osm.pbf           # OSM data (mounted from host)
  └── graph-cache/      # Preprocessed routing graph (persisted)
```

---

## Performance Notes

### First Run (Graph Import)

- **Europe OSM (~30GB):** 30-60 minutes on modern CPU
- **RAM usage:** ~16-32GB recommended
- **Disk I/O:** Heavy writes to graph cache

### Subsequent Runs

- **Startup time:** ~30 seconds
- **RAM usage:** Stable after warm-up
- **Disk I/O:** Minimal (reads cached graph)

### Cache Persistence

The graph cache in `/data/graph-cache` must be persisted across container restarts:
- ✅ **Do:** Use a Docker volume or bind mount
- ❌ **Don't:** Store cache inside container (will rebuild on every run)

---

## Troubleshooting

### Build Fails with Git Errors

```bash
# Check GitHub connectivity
docker run --rm alpine/git --version
docker run --rm alpine/git clone --depth 1 https://github.com/graphhopper/graphhopper.git
```

### Container Exits Immediately

```bash
# Check logs for errors
docker logs graphhopper-eu

# Common issues:
# - Missing osm.pbf file
# - Insufficient memory (increase JAVA_OPTS)
# - Port 8989 already in use
```

### Out of Memory During Import

```bash
# Increase heap size
docker run -d \
  --name graphhopper-eu \
  -p 8989:8989 \
  -e JAVA_OPTS="-Xms16g -Xmx32g" \
  ...
```

### Slow API Responses

- Ensure CH graph is fully built (check logs for "preparation finished")
- Increase JVM memory if GC pressure is high
- Use SSD storage for graph cache

---

## Development Notes

### No Host Dependencies Required

This build requires **only Docker**. No need to install:
- ❌ JDK/JRE
- ❌ Maven
- ❌ GraphHopper
- ❌ Git

Everything happens inside the container.

### Version Support

Build any GraphHopper version:

```bash
# Version 8.0
docker build --build-arg GH_VERSION=8.0 -t dynop-graphhopper:8.0 .

# Version 11.0
docker build --build-arg GH_VERSION=11.0 -t dynop-graphhopper:11.0 .

# Version 11.0 (default)
docker build -t dynop-graphhopper:11.0 .

# Latest development
docker build --build-arg GH_VERSION=master -t dynop-graphhopper:latest .
```

### CI/CD Integration

This Dockerfile is CI/CD-ready:

```yaml
# Example GitHub Actions
- name: Build GraphHopper
  run: |
    cd devtools/graphhopper-build
    docker build -t dynop-graphhopper:${{ github.sha }} .
```

---

## License

GraphHopper is licensed under Apache 2.0.  
See: https://github.com/graphhopper/graphhopper/blob/master/LICENSE.txt

---

## Support

For GraphHopper-specific issues:
- GitHub: https://github.com/graphhopper/graphhopper
- Docs: https://docs.graphhopper.com/

For Docker build issues:
- Review logs: `docker logs graphhopper-eu`
- Check disk space: `df -h`
- Verify Docker version: `docker --version` (>= 20.10 recommended)
