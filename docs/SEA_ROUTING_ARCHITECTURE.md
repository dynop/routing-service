# Sea Routing Technical Architecture

## System Overview

The DynOp Ocean Routing Engine extends the existing GraphHopper-based road routing service with global maritime routing capabilities. This document describes the technical architecture, design decisions, and component interactions.

## Design Principles

### 1. GraphHopper-Native Implementation
All routing logic uses GraphHopper's native APIs:
- `BaseGraph` for node/edge storage
- `LocationIndex` for spatial queries
- `EdgeFilter` for query-time constraints
- CH (Contraction Hierarchies) for fast routing

### 2. Build-Time vs Runtime Separation
- **Build-time**: All geometry operations (land intersection, chokepoint assignment)
- **Runtime**: Pure graph traversal with node-based filtering
- **Rationale**: Geometry operations are expensive; CH queries are O(log n)

### 3. Backward Compatibility
- `mode=road` (default) maintains existing behavior
- All new fields are optional
- Response structure extends, never replaces

## Component Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           BUILD TIME (Offline)                           │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌────────────────────┐    ┌─────────────────┐    ┌──────────────────┐  │
│  │ Natural Earth      │    │ SeaLaneGraph    │    │ GraphHopper      │  │
│  │ Shapefile (50m)    │───▶│ Builder         │───▶│ BaseGraph        │  │
│  │ (ne_50m_land.shp)  │    │                 │    │ (nodes, edges)   │  │
│  └────────────────────┘    └─────────────────┘    └──────────────────┘  │
│                                    │                        │            │
│                                    │                        ▼            │
│                                    │              ┌──────────────────┐  │
│                                    │              │ CH Preparation   │  │
│                                    │              │ (shortcuts)      │  │
│                                    │              └──────────────────┘  │
│                                    ▼                                     │
│                          ┌─────────────────────┐                        │
│                          │ chokepoint_metadata │                        │
│                          │ .json               │                        │
│                          └─────────────────────┘                        │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                           RUNTIME (Online)                               │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐     ┌─────────────────────────────────────────────┐   │
│  │ API Request  │────▶│               MatrixResource                │   │
│  │ mode=sea     │     │                                             │   │
│  └──────────────┘     │  1. Mode Selection (road/sea)               │   │
│                       │  2. Port Snapping (Stage 1 + 2)             │   │
│                       │  3. Chokepoint Filter Creation              │   │
│                       │  4. Matrix Computation                      │   │
│                       └─────────────────────────────────────────────┘   │
│                                         │                                │
│                    ┌────────────────────┼────────────────────┐          │
│                    ▼                    ▼                    ▼          │
│           ┌───────────────┐   ┌───────────────┐   ┌────────────────┐   │
│           │ UnlocodePort  │   │ SeaNodeSnapper│   │ Chokepoint     │   │
│           │ Snapper       │   │               │   │ AwareEdgeFilter│   │
│           │ (Stage 1)     │   │ (Stage 2)     │   │                │   │
│           └───────────────┘   └───────────────┘   └────────────────┘   │
│                    │                    │                    │          │
│                    ▼                    ▼                    ▼          │
│           ┌───────────────┐   ┌───────────────┐   ┌────────────────┐   │
│           │ Port List     │   │ LocationIndex │   │ Excluded Node  │   │
│           │ (UN/LOCODE)   │   │ (sea graph)   │   │ Set            │   │
│           └───────────────┘   └───────────────┘   └────────────────┘   │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

## Sea Lane Graph Construction

### Waypoint Grid Generation

```
Latitude range:  -80° to +80° (excludes polar regions)
Longitude range: -180° to +180°
Step size:       5° (configurable)

Total waypoints: (160/5 + 1) × (360/5) = 33 × 72 = 2,376
```

### Land Filtering Algorithm

```java
for each waypoint (lat, lon):
    point = geometryFactory.createPoint(lon, lat)
    if landGeometry.contains(point):
        reject waypoint (on land)
    else:
        add to water waypoints
```

### Chokepoint Densification

Each mandatory chokepoint gets additional waypoints:
- Center point at chokepoint coordinates
- Ring of points at `step_degrees` intervals within `radius_degrees`
- All densified points marked with chokepoint ID

```
SUEZ:    center=(30.585, 32.265), radius=0.5°, step=0.1°
PANAMA:  center=(9.0, -79.5),    radius=0.5°, step=0.1°
MALACCA: center=(2.5, 101.5),    radius=1.0°, step=0.2°
```

### Edge Construction

K-Nearest Neighbors approach:
1. For each water waypoint, find k=6 nearest neighbors
2. Create bidirectional edges to each neighbor
3. Reject edge if it intersects land geometry
4. Handle antimeridian crossing (-180°/+180° wrap)

### Connectivity Validation

BFS from arbitrary water node:
- If all water nodes reachable → single connected component ✓
- If islands detected → warning in build summary

## Port Snapping Pipeline

### Stage 1: User Coord → UN/LOCODE Port

```
Input:  User coordinate (lat, lon)
        Port role (POL/POD)
        Max snap distance (300km)

Process:
1. Load all seaports from UN/LOCODE CSVs
2. Filter: function[0]='1' (seaport), valid status, valid coords
3. Calculate Haversine distance to each port
4. Select nearest within max distance

Output: PortSnapResult {
    unlocode: "NLRTM",
    name: "Rotterdam",
    lat: 51.9167,
    lon: 4.5,
    snap_distance_km: 12.5,
    role: PORT_OF_LOADING
}
```

### Stage 2: Port Coord → Sea Graph Node

```
Input:  Port coordinate from Stage 1
        EdgeFilter (chokepoint exclusions)

Process:
1. Query LocationIndex with port coords
2. Apply EdgeFilter to exclude chokepoint nodes
3. Find closest valid graph node

Output: Snap object with closest node ID
```

## Chokepoint Exclusion Design

### Why Node-Based Filtering?

**Alternative considered**: Pre-compute multiple graphs (one per chokepoint combination)
- Problem: 2^8 = 256 graph variants for 8 chokepoints
- Memory: 256 × graph_size unacceptable

**Chosen approach**: Single graph + query-time EdgeFilter
- Filter checks: `excludedNodes.contains(edge.baseNode) || excludedNodes.contains(edge.adjNode)`
- Trade-off: CH shortcuts may include excluded nodes → fallback to A*
- Acceptable: Most queries don't exclude chokepoints

### EdgeFilter Implementation

```java
public class ChokepointAwareEdgeFilter implements EdgeFilter {
    private final Set<Integer> excludedNodeIds;
    
    public boolean accept(EdgeIteratorState edge) {
        return !excludedNodeIds.contains(edge.getBaseNode()) 
            && !excludedNodeIds.contains(edge.getAdjNode());
    }
}
```

### Chokepoint Metadata Storage

```json
{
  "chokepoints": [
    {
      "id": "SUEZ",
      "name": "Suez Canal",
      "region": "Middle East",
      "lat": 30.585,
      "lon": 32.265,
      "radiusDegrees": 0.5,
      "nodeIds": [1423, 1424, 1425, 1426, ...]
    }
  ]
}
```

## Contraction Hierarchies for Sea Routing

### Why CH Works Well for Sea Graphs

1. **Low node degree**: Sea graph has ~6 neighbors per node
2. **Uniform structure**: Regular grid with chokepoint clusters
3. **No turn restrictions**: Ships can turn freely
4. **Single vehicle type**: No need for multiple weightings

### CH Preparation

```yaml
profiles_ch:
  - profile: ship
```

Preparation time: ~30 seconds for 5° grid

### CH Query Behavior

With chokepoint exclusions:
- If shortcut contains excluded node → shortcut rejected
- Falls back to original edges
- May need LM fallback for extreme exclusions

## Routing Engine Registry

### Dual Hopper Design

```java
public class RoutingEngineRegistry {
    private final GraphHopper roadHopper;  // Truck routing
    private final GraphHopper seaHopper;   // Ship routing
    
    public GraphHopper getHopper(RoutingMode mode) {
        return mode == RoutingMode.SEA ? seaHopper : roadHopper;
    }
}
```

### Initialization in MatrixBundle

```java
// Load road hopper (existing)
GraphHopper roadHopper = configuration.getGraphHopper();

// Load sea hopper (new)
GraphHopper seaHopper = loadSeaHopper(configuration);

// Create registry
RoutingEngineRegistry registry = new RoutingEngineRegistry(roadHopper, seaHopper);

// Register with HK2
environment.jersey().register(new AbstractBinder() {
    protected void configure() {
        bind(registry).to(RoutingEngineRegistry.class);
    }
});
```

## Matrix Computation Flow

### Road Mode (Original)

```
1. Validate request
2. Snap points to road graph
3. Build QueryGraph
4. Create CH algorithm
5. Compute N×M routes in parallel
6. Return distances/times
```

### Sea Mode (Extended)

```
1. Validate request
2. Check sea routing availability
3. Stage 1 snap: coords → ports
4. Stage 2 snap: ports → sea nodes
5. Create ChokepointAwareEdgeFilter (if exclusions)
6. Build QueryGraph
7. Create CH algorithm (or fallback)
8. Compute N×M routes in parallel
9. Return distances/times + port metadata
```

## Error Handling

### Error Codes

| Code | Cause | Resolution |
|------|-------|------------|
| `SEA_ROUTING_UNAVAILABLE` | Sea graph not loaded | Build sea graph |
| `PORT_DATA_UNAVAILABLE` | UN/LOCODE not loaded | Check unlocode-data/ |
| `NO_SEAPORT_FOUND` | Empty port list | Check CSV loading |
| `NO_SEAPORT_WITHIN_RANGE` | Point >300km from port | Use valid sea location |
| `COORDINATE_ON_LAND` | Point clearly inland | Use coastal coordinate |
| `POLAR_REGION_UNSUPPORTED` | Latitude >80° | Use lower latitude |

### Graceful Degradation

1. Sea routing unavailable → Error response (not 500)
2. Port snap fails → Point marked as failure, others continue
3. CH unavailable → Fall back to LM or Dijkstra
4. Single route fails → Matrix continues, -1 for failed cell

## Performance Characteristics

### Memory Usage

| Component | Memory |
|-----------|--------|
| Sea graph (5°) | ~50 MB |
| Sea graph (1°) | ~500 MB |
| Port list (40k) | ~20 MB |
| Chokepoint metadata | ~1 MB |

### Query Latency

| Operation | Time |
|-----------|------|
| Port snap (Stage 1) | <1 ms per point |
| Graph snap (Stage 2) | <1 ms per point |
| CH route | <1 ms per pair |
| LM route | 5-20 ms per pair |
| Full matrix (10×10) | 10-50 ms |

## Future Considerations

### Potential Enhancements

1. **Weather routing**: Integrate wind/current data
2. **ECA zones**: Emission Control Area awareness
3. **Piracy zones**: High-risk area avoidance
4. **Seasonal routing**: Ice coverage in polar approaches
5. **Multi-modal**: Combined road + sea optimization

### Scalability

- Graph can be sharded by region
- Port snapping supports spatial indexing (R-tree)
- Matrix computation is embarrassingly parallel
