# GraphHopper Matrix Server (Hermetic Docker Build)

This module provides a completely hermetic build of the GraphHopper + Matrix extension with **road and sea routing** support. Maven never runs on the host; Docker builds the shaded JAR and launches Dropwizard with the `MatrixBundle`, which automatically registers `/custom/matrix`.

## Prerequisites
- Docker 24+
- Docker Compose V2
- OSM data file mounted at `osm-data/europe/europe-latest.osm.pbf` relative to the project root
- Natural Earth data in `natural-earth-data/` (for sea routing land mask)
- UN/LOCODE port data in `unlocode-data/` (for sea routing port snapping)

## Build & Run
```bash
cd devtools/graphhopper-build
docker-compose up --build
```
This command performs the multi-stage build (builder image compiles the shaded JAR, runtime image runs it) and starts the container named `gh-matrix` on port `8080`.

## Mounted Volumes
- `../../osm-data/europe` → `/app/osm` (read-only PBF input for road routing)
- `./graph-cache` → `/app/graph-cache` (persists road graph cache)
- `./graph-cache/sea` → `/app/graph-cache/sea` (persists sea graph cache)

Ensure the `graph-cache` directories exist before the first run or Docker will create them as root-owned:
```bash
mkdir -p devtools/graphhopper-build/graph-cache
mkdir -p devtools/graphhopper-build/graph-cache/sea
```

## Building the Sea Graph

Before using sea routing, you need to build the sea lane graph:

```bash
# Build the project first
cd matrix-extension
mvn clean package -DskipTests

# Run the sea lane graph builder
java -cp target/classes:target/dependency/* \
  com.dynop.graphhopper.matrix.sea.builder.SeaLaneGraphBuilder \
  --output ../devtools/graphhopper-build/graph-cache/sea \
  --landmask ../natural-earth-data/ne_50m_land.shp \
  --step 5
```

The builder creates a 5° grid of sea waypoints and CH shortcuts for fast routing.

## Matrix API Smoke Tests

### Road Routing (Truck)
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

### Sea Routing (Ship)
```bash
curl -X POST http://localhost:8080/custom/matrix \
  -H "Content-Type: application/json" \
  -d '{
        "mode": "sea",
        "points": [[51.9167, 4.5], [1.2833, 103.85]],
        "sources": [0],
        "targets": [1],
        "profile": "ship",
        "metrics": ["distance", "time"]
      }'
```

### Sea Routing with Chokepoint Exclusion
```bash
curl -X POST http://localhost:8080/custom/matrix \
  -H "Content-Type: application/json" \
  -d '{
        "mode": "sea",
        "points": [[51.9167, 4.5], [1.2833, 103.85]],
        "sources": [0],
        "targets": [1],
        "profile": "ship",
        "excluded_chokepoints": ["SUEZ"]
      }'
```

## Configuration

### Road Routing
`config.yml` configures road routing:
- OSM path: `/app/osm/europe-latest.osm.pbf`
- Graph cache: `/app/graph-cache`
- Truck custom model: `/app/dynop-truck.json`
- HTTP port: `8080`

### Sea Routing
`config.yml` also includes sea routing configuration:
- Sea graph: `/app/graph-cache/sea`
- UN/LOCODE data: `/app/unlocode-data`
- Land mask: `/app/natural-earth-data/ne_50m_land.shp`
- Ship custom model: `/app/dynop-ship.json` (max speed: 25 km/h)
- Port snapping distance: 300km

### Ship Speed Configuration
Edit `dynop-ship.json` to adjust ship speed:
```json
{
  "speed": [
    {"if": "true", "limit_to": 25}
  ],
  "distance_influence": 0.001
}
```

Update `config.yml`, `dynop-truck.json`, or `dynop-ship.json` as needed, then re-run `docker-compose up --build` to rebuild the image.

## Maintenance Tips
- **Clean build cache:** `docker builder prune` (optional) if you need to reclaim space.
- **Reset road graph cache:** delete `devtools/graphhopper-build/graph-cache` (but keep `graph-cache/sea`) when adding new encoded values or OSM data.
- **Reset sea graph cache:** delete `devtools/graphhopper-build/graph-cache/sea` when modifying sea routing grid or chokepoints.
- **Logs:** `docker compose logs -f gh-matrix` shows Dropwizard output and matrix request metrics.
- **Shutdown:** `docker compose down` stops the container but keeps the persisted cache directories.

## Troubleshooting

### Sea routing unavailable
- Ensure sea graph is built in `graph-cache/sea/`
- Check that `sea.graph.location` is correctly set in `config.yml`

### Port snap failures
- Points may be >300km from any seaport
- Points in polar regions (>80° latitude) are not supported

### Chokepoint exclusions not working
- Verify chokepoint ID is valid (SUEZ, PANAMA, MALACCA, etc.)
- Check `chokepoint_metadata.json` exists in sea graph directory
