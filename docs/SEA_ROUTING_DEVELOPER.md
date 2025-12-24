# Sea Routing Developer Guide

## Overview

This guide covers the technical implementation of sea routing in the DynOp Routing Service, including graph building, configuration, and debugging.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Matrix API Endpoint                          │
│                     POST /custom/matrix                             │
├─────────────────────────────────────────────────────────────────────┤
│                         MatrixResource                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │
│  │ mode=road    │  │ mode=sea     │  │                          │   │
│  │              │  │              │  │  RoutingEngineRegistry   │   │
│  │ Road Hopper  │  │ Sea Hopper   │  │  - roadHopper            │   │
│  │ (truck)      │  │ (ship)       │  │  - seaHopper             │   │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│                      Port Snapping Pipeline                          │
│  ┌──────────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │ User Coordinate  │───▶│ UnlocodePort     │───▶│ SeaNodeSnapper│  │
│  │ (lat, lon)       │    │ Snapper (Stage1) │    │ (Stage 2)     │  │
│  └──────────────────┘    └──────────────────┘    └───────────────┘  │
├─────────────────────────────────────────────────────────────────────┤
│                    Chokepoint Management                             │
│  ┌──────────────────────┐    ┌────────────────────────────────────┐ │
│  │ ChokepointRegistry   │───▶│ ChokepointAwareEdgeFilter         │ │
│  │ (metadata JSON)      │    │ (excludes nodes at query time)    │ │
│  └──────────────────────┘    └────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Building the Sea Graph

### Prerequisites

1. **Natural Earth Data**: `natural-earth-data/ne_50m_land.shp`
2. **Java 17+**: For running the builder
3. **Maven**: For building the project

### Running the Graph Builder

```bash
# Build the project
cd matrix-extension
mvn clean package -DskipTests

# Run the sea lane graph builder
java -cp target/classes:target/dependency/* \
  com.dynop.graphhopper.matrix.sea.builder.SeaLaneGraphBuilder \
  --output ../devtools/graphhopper-build/graph-cache/sea \
  --landmask ../natural-earth-data/ne_50m_land.shp \
  --step 5
```

### Builder Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `--output` | `graph-cache/sea` | Output directory for graph files |
| `--landmask` | `natural-earth-data/ne_50m_land.shp` | Path to land polygon shapefile |
| `--step` | `5` | Grid spacing in degrees (smaller = more nodes) |

### Builder Output

```
graph-cache/sea/
├── nodes                    # Node storage
├── edges                    # Edge storage
├── geometry                 # Edge geometries
├── location_index          # Spatial index
├── properties              # Graph properties
├── nodes_ch_ship           # CH shortcuts
├── shortcuts_ship          # CH data
├── chokepoint_metadata.json # Chokepoint node mappings
└── build_summary.json      # Build statistics
```

### Build Summary Example

```json
{
  "total_waypoints": 2592,
  "water_waypoints": 1847,
  "total_edges": 8234,
  "rejected_edges_land_crossing": 1203,
  "chokepoints": [
    {"id": "SUEZ", "nodes": 12},
    {"id": "PANAMA", "nodes": 8},
    {"id": "MALACCA", "nodes": 15}
  ],
  "connected_components": 1,
  "build_time_seconds": 45
}
```

## Configuration

### Application Configuration

Add sea routing configuration to your `config.yml`:

```yaml
graphhopper:
  # Road graph (existing)
  graph.location: graph-cache/road

  # Sea graph configuration
  sea:
    graph.location: graph-cache/sea
    unlocode.directory: unlocode-data
    port_snapping:
      max_snap_distance_km: 300.0
```

### Sea Profile Configuration

The `sea-config.yml` file (for CH preparation):

```yaml
graphhopper:
  graph.location: graph-cache/sea
  
  profiles:
    - name: ship
      vehicle: ship
      weighting: fastest
      custom_model_files:
        - dynop-ship.json
      turn_costs: false
  
  profiles_ch:
    - profile: ship
```

### Custom Ship Model

The `dynop-ship.json` file defines ship speed:

```json
{
  "speed": [
    {
      "if": "true",
      "limit_to": 30
    }
  ],
  "distance_influence": 0.001
}
```

## API Integration

### Request Structure

```java
MatrixRequest request = new MatrixRequest(
    points,           // List<List<Double>> - [lat, lon] pairs
    sources,          // List<Integer> - source indices
    targets,          // List<Integer> - target indices  
    "ship",           // profile
    List.of("distance", "time"), // metrics
    true              // enable_fallback
);
request.setMode(RoutingMode.SEA);
request.setExcludedChokepoints(List.of("SUEZ", "PANAMA"));
```

### Response Structure

```java
MatrixResponse response = ...;
response.getMode();              // RoutingMode.SEA
response.getDistances();         // long[][] in meters
response.getTimes();             // long[][] in milliseconds
response.getFailures();          // List<Integer> failed indices
response.getPortSnaps();         // List<PortSnapResult> snap metadata
response.getExcludedChokepoints(); // List<String> applied exclusions
```

## Debugging

### Common Issues

#### 1. "Sea routing is not configured"
- Check that `graph-cache/sea/` exists and contains valid graph files
- Verify `sea.graph.location` in configuration

#### 2. "UN/LOCODE port data is not loaded"
- Ensure `unlocode-data/` directory exists with CSV files
- Check CSV files match pattern `*UNLOCODE*.csv`

#### 3. "No sea profile configured"
- Sea graph must have a profile named `ship` or specify first available
- Rebuild CH if profile was added after initial build

#### 4. Port snap failures
- Point may be >300km from any seaport
- Point may be in polar regions (>80° latitude)
- Check `failures` array in response

### Logging

Enable debug logging for sea routing:

```yaml
logging:
  loggers:
    com.dynop.graphhopper.matrix.sea: DEBUG
    com.dynop.graphhopper.matrix.config.MatrixBundle: DEBUG
```

### Metrics

Monitor these metrics:
- `matrix.requests.latency` - Request timing
- `matrix.routes.per_second` - Throughput

## Testing

### Unit Tests

```bash
mvn test -Dtest="**/sea/*Test"
```

### Integration Tests

```bash
# Requires built sea graph
mvn verify -Pit
```

### Manual Testing

```bash
# Start server
java -jar target/matrix-extension.jar server config.yml

# Test sea routing
curl -X POST http://localhost:8989/custom/matrix \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "sea",
    "points": [[51.9167, 4.5], [1.2833, 103.85]],
    "sources": [0],
    "targets": [1],
    "profile": "ship"
  }'
```

## Performance Considerations

### Graph Size
- 5° grid: ~1,800 water nodes, ~8,000 edges
- 2.5° grid: ~7,000 water nodes, ~35,000 edges
- 1° grid: ~45,000 water nodes, ~200,000 edges

### CH Preparation
- 5° grid: ~30 seconds
- 2.5° grid: ~2 minutes
- 1° grid: ~15 minutes

### Query Performance
- With CH: <1ms per route
- Without CH (fallback): 10-50ms per route

## Extending the System

### Adding New Chokepoints

1. Add entry to `SeaLaneGraphBuilder.MANDATORY_CHOKEPOINTS`
2. Rebuild the sea graph
3. Chokepoint will be available in API

### Custom Speed Models

Create a new JSON file:

```json
{
  "speed": [
    {"if": "true", "limit_to": 40}
  ]
}
```

Reference in profile configuration.

### Additional Port Data

UN/LOCODE updates are published semi-annually. To update:
1. Download new CSV files from UNECE
2. Replace files in `unlocode-data/`
3. Restart server (ports reload on startup)

## Troubleshooting

### Graph Not Connected
If `build_summary.json` shows multiple connected components:
1. Check land mask coverage
2. Reduce grid step size
3. Verify chokepoint coordinates

### Memory Issues
For large graphs (1° step):
```bash
java -Xmx8g -jar ... SeaLaneGraphBuilder --step 1
```

### CH Preparation Hangs
- Increase timeout in configuration
- Use smaller grid step
- Ensure graph is connected (single component)
