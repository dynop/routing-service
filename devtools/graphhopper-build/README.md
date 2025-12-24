# GraphHopper Matrix Server (Hermetic Docker Build)

This module provides a completely hermetic build of the GraphHopper + Matrix extension. Maven never runs on the host; Docker builds the shaded JAR and launches Dropwizard with the `MatrixBundle`, which automatically registers `/custom/matrix`.

## Prerequisites
- Docker 24+
- Docker Compose V2
- OSM data file mounted at `osm-data/europe/europe-latest.osm.pbf` relative to the project root

## Build & Run
```bash
cd devtools/graphhopper-build
docker-compose up --build
```
This command performs the multi-stage build (builder image compiles the shaded JAR, runtime image runs it) and starts the container named `gh-matrix` on port `8080`.

## Mounted Volumes
- `../../osm-data/europe` → `/app/osm` (read-only PBF input)
- `./graph-cache` → `/app/graph-cache` (persists GraphHopper cache between restarts)

Ensure the `graph-cache` directory exists before the first run or Docker will create it as root-owned:
```bash
mkdir -p devtools/graphhopper-build/graph-cache
```

## Matrix API Smoke Test
Once the container prints `MatrixBundle` startup logs, the custom endpoint is live:
```bash
curl -X POST http://localhost:8080/custom/matrix \
  -H "Content-Type: application/json" \
  -d '{
        "points": [[48.1,16.2],[48.2,16.3]],
        "profile": "truck",
        "metrics": ["distance","time"]
      }'
```
Successful responses confirm that `MatrixBundle` registered and `/custom/matrix` is active.

## Configuration
`config.yml` already targets the Docker layout:
- OSM path: `/app/osm/europe-latest.osm.pbf`
- Graph cache: `/app/graph-cache`
- Truck custom model: `/app/dynop-truck.json`
- HTTP port: `8080`

Update `config.yml` or `dynop-truck.json` as needed, then re-run `docker-compose up --build` to rebuild the image.

## Maintenance Tips
- **Clean build cache:** `docker builder prune` (optional) if you need to reclaim space.
- **Reset GraphHopper cache:** delete `devtools/graphhopper-build/graph-cache` when adding new encoded values or OSM data.
- **Logs:** `docker compose logs -f gh-matrix` shows Dropwizard output and matrix request metrics.
- **Shutdown:** `docker compose down` stops the container but keeps the persisted cache directory.
