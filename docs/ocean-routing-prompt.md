# 🌊 GitHub Copilot Prompt — Sea-Lane Graph Builder (GraphHopper-Native, Java)

## 🚨 FULLY UPDATED — DO NOT DEVIATE 🚨

This prompt replaces all previous versions.  
It defines the **mandatory, production-grade requirements** for building and operating the global sea-lane graph in dynop.

---

## 👤 Role

You are a **Senior Java Backend & Geospatial Engineer** working on the dynop platform.

You are extending an **existing Java/Maven, Dropwizard, GraphHopper 11.0–based routing service** (with CH/LM and a custom Matrix API) to support **global ocean freight routing** using a **GraphHopper-native sea-lane graph**.

The system already supports truck routing with GraphHopper and a custom Matrix API.  
Sea routing MUST integrate cleanly without introducing parallel routing stacks.

---

## 🎯 Objective

Implement a **GraphHopper-native Sea-Lane Graph Builder** and integrate it into the existing routing server so that:

- 🚛 Truck routing continues to use the existing road graph
- 🚢 Sea routing uses a **separate maritime graph** built offline
- 🔄 The existing **Matrix API** can be reused for sea routing
- ✅ Outputs are **deterministic, reproducible, and enterprise-grade**
- 🌐 **Global connectivity is validated** (no disconnected ocean basins)
- 🔀 **Chokepoints are scenario-controllable** (query-time exclusion)

### 📦 Additionally, deliver:

- Automated tests with connectivity validation
- Business documentation
- Developer documentation
- Technical architecture documentation
- Build artifacts & runtime metrics

---

## ⚠️ Hard Constraints (STRICT)

### 🏗️ Architecture

- ✅ Java 17 only (Maven project, matches existing `matrix-extension`)
- ✅ Reuse **GraphHopper Core** graph format and routing APIs (version defined in `matrix-extension/pom.xml` → `${graphhopper.version}`)
- ✅ Follow existing **HK2 dependency injection** patterns (see `MatrixBundle`)
- ✅ Natural Earth **50m** used ONLY as a **land mask** (NOT converted to routing graph)
- ✅ UN/LOCODE ports are authoritative maritime endpoints
- ❌ NO standalone Dijkstra implementations
- ❌ NO Python runtime or microservice
- ❌ NO second routing engine (use multiple GraphHopper instances)
- ❌ NO converting Natural Earth geometry into a routing graph

### 📜 Data & Legal

- ❌ NO scraping
- ❌ NO proprietary maritime data (Sea-Distances, AIS, MarineTraffic)
- ❌ NO dynamic land polygon assembly from OSM PBF (too complex, unsafe)
- ✅ ONLY OpenStreetMap data already present in the project (for road routing)
- ✅ **Natural Earth land polygons** for ocean land-masking (bundled GeoJSON)
- ✅ UN/LOCODE port list with coordinates is authoritative

### 📚 Required Dependencies

Add to `pom.xml`:

```xml
<!-- JTS for land geometry filtering -->
<dependency>
    <groupId>org.locationtech.jts</groupId>
    <artifactId>jts-core</artifactId>
    <version>1.19.0</version>
</dependency>
```

### 📁 Required Data

Bundle in resources:

```
resources/
└── natural-earth/
    └── ne_50m_land.geojson   # Land polygons for ocean masking (50m resolution)
```

**Source:** [Natural Earth Vector](https://github.com/nvkelso/natural-earth-vector/tree/master/geojson)

- Use `ne_50m_land.geojson` for accurate coastline masking near ports and chokepoints
- 50m resolution provides good balance between accuracy and file size (~5MB)
- **DO NOT** attempt to derive land geometry from OSM PBF at runtime

---

## 🔧 Critical Implementation Fixes (NON-NEGOTIABLE)

The following three fixes are **structural correctness requirements**, not optimizations.  
**Failure to implement any one will cause global ocean routing to fail in production.**

### 1️⃣ 🌐 Antimeridian Handling

- **Problem:** Standard KNN distance treats `+179°` and `−179°` as far apart, breaking Pacific routes
- **Fix:** Neighbor search MUST be **dateline-aware** using `lon ± 360°` for distance calculation
- **Result:** Shanghai ↔ Los Angeles routes correctly cross Pacific (not forced via Suez/Panama)

### 2️⃣ 🗺️ Land Mask Source

- **Problem:** Deriving land polygons from OSM PBF is too complex and unsafe for inline builder
- **Fix:** Use **prebuilt Natural Earth land polygons** (bundled GeoJSON), not dynamic OSM assembly
- **Result:** Reliable land avoidance, predictable geometry, controlled scope

### 3️⃣ 🔍 Chokepoint Densification

- **Problem:** Coarse 5° grid is insufficient near narrow straits (Gibraltar, Bosphorus, etc.)
- **Fix:** Locally **densify waypoint grid** around chokepoints (0.5°–1° step within 2–3° radius)
- **Result:** Stable, water-only chokepoint connectivity, no false edge rejection

---

## 🌐 Global Connectivity & Validation (CRITICAL)

### 1️⃣ Antimeridian Handling (MANDATORY)

- Neighbor search MUST be **dateline-aware**
- Waypoints near `+180°` and `−180°` longitude MUST connect correctly
- Implement by considering `lon ± 360°` during distance computation
- **Failure to do this will invalidate Pacific routing and is unacceptable**

### 2️⃣ Global Connectivity Validation (MANDATORY)

After graph construction, the builder MUST:

- Compute **connected components** of the sea graph
- The build MUST **FAIL** if:
  - The Pacific Ocean is disconnected across the antimeridian
  - Any major ocean basin is unintentionally isolated

**Mandatory connectivity checks (integration tests):**

| Route | Purpose |
|-------|---------|
| Tokyo ↔ Los Angeles | Pacific, dateline crossing |
| Shanghai ↔ Rotterdam | Asia ↔ Europe baseline |
| Shanghai ↔ Rotterdam (Suez excluded) | Redundancy via Cape |

### 3️⃣ Chokepoint Redundancy (REAL-WORLD CORRECTNESS)

Major chokepoints MUST NOT be modeled as single points of failure.

**Asia ↔ Europe routing MUST:**
- Use Suez when available
- Reroute via **Cape of Good Hope** when Suez is unavailable
- NEVER fail or route via unrealistic corridors (e.g., Panama for Asia↔Europe)

---

## 🔀 Chokepoints as Controllable Features (MANDATORY)

### Concept

Chokepoints are **scenario-controlled constraints**, not hard-coded routing rules.

They must be:
- **Identifiable** — stable IDs for each chokepoint
- **Switchable** — enabled/disabled at query time
- **Auditable** — exclusions logged and traceable
- **Cache-safe** — exclusion list part of cache keys

### Chokepoint Model (REQUIRED)

Each chokepoint MUST be represented as a first-class domain object:

```java
public class Chokepoint {
    private final String id;              // e.g., "SUEZ", "PANAMA", "CAPE_GOOD_HOPE"
    private final String name;            // Human-readable name
    private final String region;          // Optional grouping (e.g., "AFRICA", "AMERICAS")
    private final Set<Integer> nodeIds;   // Graph nodes belonging to this chokepoint
    private boolean enabled;              // Default = true
}
```

**Rules:**
- IDs MUST be stable and versioned
- Nodes belonging to a chokepoint MUST be explicitly tagged at graph build time
- Chokepoints MAY consist of:
  - One node (canal entry point)
  - A small node cluster (straits, capes)

### Routing Query Support (REQUIRED)

All sea routing and matrix queries MUST support scenario-level exclusion:

```java
// In MatrixRequest.java
@JsonProperty("excluded_chokepoints")
private final List<String> excludedChokepoints;  // e.g., ["SUEZ", "PANAMA"]
```

**Behavior:**
- If a chokepoint is excluded:
  - ALL nodes/edges associated with that chokepoint are treated as **non-traversable**
- Exclusion MUST be:
  - **Deterministic** — same exclusions = same results
  - **Applied at query time** — NOT by mutating the graph
- The exclusion list MUST be part of:
  - Routing cache keys
  - Result metadata

### Mandatory Scenario Behavior

| Scenario | Excluded Chokepoints | Expected Behavior |
|----------|---------------------|-------------------|
| Baseline | `[]` | Shanghai → Rotterdam via Suez |
| Suez closed | `["SUEZ"]` | Route exists via Cape of Good Hope, distance increases significantly |
| Panama closed | `["PANAMA"]` | US East ↔ Asia routes via Suez or Cape |

**Invalid behavior (FORBIDDEN):**
- ❌ Route failure when alternative exists
- ❌ Reroute via Panama for Asia ↔ Europe
- ❌ Silent fallback without logging exclusions

### Implementation Approach

```java
public class ChokepointAwareEdgeFilter implements EdgeFilter {
    private final Set<Integer> excludedNodeIds;
    
    public ChokepointAwareEdgeFilter(List<String> excludedChokepoints, 
                                      ChokepointRegistry registry) {
        this.excludedNodeIds = excludedChokepoints.stream()
            .flatMap(id -> registry.getChokepoint(id).getNodeIds().stream())
            .collect(Collectors.toSet());
    }
    
    @Override
    public boolean accept(EdgeIteratorState edge) {
        return !excludedNodeIds.contains(edge.getBaseNode()) 
            && !excludedNodeIds.contains(edge.getAdjNode());
    }
}
```

---

## 🏛️ High-Level Design (MANDATORY)

### 📂 Directory Structure

```
graph-cache/
├── road/           # existing truck graph (migrate from current graph-cache/)
└── sea/            # new maritime graph
```

---

## 🏛️ Assumed System Architecture (ANTI-HALLUCINATION GUARDRAIL)

The implementation MUST assume and integrate with this existing structure:

```
Dropwizard App (MatrixServerApplication)
├── RoutingEngineRegistry         # holds all hoppers
│   ├── GraphHopper roadHopper    # profile: truck
│   └── GraphHopper seaHopper     # profile: ship
├── ChokepointRegistry            # NEW: manages chokepoint metadata
├── MatrixBundle                  # existing, extended
└── MatrixResource                # existing, extended with mode param
```

### 🔀 Mode vs Profile (IMPORTANT)

- **mode** → selects which GraphHopper instance (`road` or `sea`)
- **profile** → selects routing profile within that hopper (`truck`, `ship`)

**Rules:**
- Sea routing MUST reuse:
  - GraphHopper runtime (same engine, different graph)
  - Matrix API execution flow (same endpoint, mode parameter)
- Sea graph MUST be stored in `graph-cache/sea/`
- Mode selection occurs at API level via `MatrixRequest.mode`

**Forbidden:**
- ❌ New routing APIs
- ❌ Parallel Dijkstra engines
- ❌ Mixing road and sea graphs

### 🚀 Routing Behavior

- **Sea routing uses GraphHopper runtime** (same as road)
- **No CH required for sea graph** (use `enableFallback: true`)
- Mode selection happens at API layer via `MatrixRequest.mode`
- **Chokepoint exclusions** applied via custom EdgeFilter at query time

---

## ⚠️ Migration Checklist (One-Time)

The current graph cache is at `devtools/graphhopper-build/graph-cache/` with files in root.

Before implementing sea routing:

1. Create `graph-cache/road/` subdirectory
2. Move all existing cache files into `graph-cache/road/`
3. Update `devtools/graphhopper-build/config.yml`:
   - Change: `graph.location: /app/graph-cache` → `graph.location: /app/graph-cache/road`
4. Rebuild road graph to verify migration
5. Create empty `graph-cache/sea/` for maritime graph

---

## ✅ Implementation Tasks (MANDATORY ORDER)

### 1️⃣ Sea-Lane Graph Builder (Offline, Java)

Create a CLI/Job module: `SeaLaneGraphBuilder`

**Responsibilities:**
- Generate a **synthetic maritime graph**
- Tag chokepoint nodes with chokepoint IDs
- Validate global connectivity
- Persist in **GraphHopperStorage** format
- Emit build artifacts and metrics

#### 📋 Steps

1. **Generate global waypoint grid:**
   - Lat: −80 → +80
   - Lon: −180 → +180
   - Configurable step (default 5°)

2. **Add mandatory chokepoints:**

| Location | LAT | LONG |
|----------|-----|------|
| SUEZ | 30.812330 | 32.317903 |
| PANAMA | 9.083179 | -79.677571 |
| MALACCA | 2.5 | 101.0 |
| GIBRALTAR | 35.942918 | -5.614690 |
| BOSPHORUS | 41.097591 | 29.060623 |
| CAPE_OF_GOOD_HOPE | -34.353219 | 18.228192 |
| BAB_EL_MANDEB | 12.6 | 43.3 |
| HORMUZ | 26.5 | 56.3 |

   **Each chokepoint entry MUST also specify:**
   - `id`: Stable identifier (e.g., `"SUEZ"`)
   - `radius`: Densification radius in degrees
   - `step`: Densification step size in degrees

3. **🚨 CRITICAL: Chokepoint Densification & Tagging**
   - Coarse 5° grid is insufficient near narrow straits
   - Locally **densify the waypoint grid around each chokepoint**:
     - Smaller step size: 0.5°–1° within 2–3° radius of chokepoint
     - Connect chokepoints only to these dense local waypoints
   - **Tag all nodes within chokepoint region** with chokepoint ID
   - Apply same land-intersection validation to dense waypoints
   - This ensures stable, water-only chokepoint connectivity

4. **Load land geometry:**
   - Load **prebuilt Natural Earth land polygons** (bundled GeoJSON)
   - **DO NOT** derive land geometry from OSM PBF (too complex, unsafe)
   - Use **JTS** `GeometryFactory` to parse and prepare geometry

5. **Remove waypoints on land:**
   - Use **JTS** for point-in-polygon checks against land geometry

6. **Connect waypoints:**
   - k-nearest neighbors (k=6)
   - **🚨 CRITICAL: Antimeridian-Aware Distance Calculation**
     - Standard KNN treats `+179°` and `−179°` as far apart (WRONG)
     - During neighbor search:
       - Consider waypoints with `lon ± 360°` for distance calculation
       - Store only original node IDs (no duplicate nodes)
     - Use **Haversine distance** or **spherical geometry** to compute actual distances
     - This ensures correct Pacific connectivity (e.g., Shanghai ↔ Los Angeles)

7. **Reject edges crossing land:**
   - JTS line–polygon intersection
   - Test edge geometry against land polygons

8. **Write nodes + edges into GraphHopper BaseGraph**

9. **Persist to `graph-cache/sea/`**

10. **🚨 CRITICAL: Validate Global Connectivity**
    - Compute connected components
    - **Build MUST FAIL** if:
      - More than one major component exists
      - Pacific connectivity test fails (Tokyo ↔ Los Angeles)
      - Asia ↔ Europe connectivity test fails (with and without Suez)

11. **Compute and store build artifacts:**
    - `sea_graph_version` (stable hash)
    - `node_count`
    - `edge_count`
    - `connected_component_count`
    - `largest_component_size`
    - `build_duration_ms`
    - `chokepoint_metadata.json`

#### 🌐 Antimeridian Handling (Implementation Detail)

```java
/**
 * Compute distance considering dateline wrap-around.
 * For KNN, check both the original point and its ±360° shifted version.
 */
public double antimeridianAwareDistance(double lat1, double lon1, double lat2, double lon2) {
    double directDist = haversineDistance(lat1, lon1, lat2, lon2);
    double wrappedDist = haversineDistance(lat1, lon1 + 360, lat2, lon2);
    double wrappedDist2 = haversineDistance(lat1, lon1 - 360, lat2, lon2);
    return Math.min(directDist, Math.min(wrappedDist, wrappedDist2));
}
```

#### 🔍 Chokepoint Densification (Implementation Detail)

```java
/**
 * Generate dense local grid around a chokepoint.
 */
public List<GHPoint> densifyAroundChokepoint(double lat, double lon, 
                                              double radiusDegrees, double stepDegrees) {
    List<GHPoint> points = new ArrayList<>();
    for (double dlat = -radiusDegrees; dlat <= radiusDegrees; dlat += stepDegrees) {
        for (double dlon = -radiusDegrees; dlon <= radiusDegrees; dlon += stepDegrees) {
            double distance = Math.sqrt(dlat * dlat + dlon * dlon);
            if (distance <= radiusDegrees) {
                points.add(new GHPoint(lat + dlat, lon + dlon));
            }
        }
    }
    return points;
}

// Usage: densifyAroundChokepoint("GIBRALTAR", 35.94, -5.61, 3.0, 0.5)
```

#### 🏷️ Chokepoint Node Tagging (Implementation Detail)

```java
/**
 * Tag graph nodes belonging to a chokepoint.
 * Stored in graph properties for runtime lookup.
 */
public void tagChokepointNodes(BaseGraph graph, Chokepoint chokepoint) {
    for (int nodeId : chokepoint.getNodeIds()) {
        // Store in graph properties or separate metadata file
        chokepointNodeIndex.put(nodeId, chokepoint.getId());
    }
}

// Persist to graph-cache/sea/chokepoint_metadata.json
{
  "chokepoints": [
    {
      "id": "SUEZ",
      "name": "Suez Canal",
      "region": "AFRICA",
      "nodeIds": [1234, 1235, 1236, 1237]
    },
    ...
  ]
}
```

---

### 2️⃣ Sea Routing Profile (GraphHopper)

Create a GraphHopper profile in sea config:

```yaml
# devtools/graphhopper-build/sea-config.yml
graphhopper:
  graph.location: /app/graph-cache/sea
  
  # Minimal encoded values for sea graph (no road restrictions needed)
  graph.encoded_values: car_access, car_average_speed
  
  profiles:
    - name: ship
      custom_model_files: [dynop-ship.json]
      turn_costs: false

  # NO CH for sea (use fallback routing)
  # profiles_ch: []

# Dropwizard server (separate instance or combined)
server:
  application_connectors:
    - type: http
      port: 8990  # Different port if running separately
```

Create minimal custom model for ship:

```json
// devtools/graphhopper-build/dynop-ship.json
{
  "priority": [],
  "speed": [
    { "if": "true", "limit_to": "30" }
  ]
}
```

**Note:** GraphHopper 11.0 requires `custom_model_files` for profiles. The `vehicle:` key alone is not valid.
The ship model uses a constant 30 km/h placeholder speed; actual distance is used for routing.

#### ⚙️ Profile Characteristics

- name: `ship`
- weighting: derived from custom model (effectively `shortest`)
- edge weight: distance in meters

#### 🚫 Explicitly Disabled

- Turn costs
- Road-specific encoded values
- CH preprocessing (initially, can add later for performance)

---

### 3️⃣ Runtime Integration (Dropwizard)

At service startup:
- Load `roadHopper` from `graph-cache/road` (migrate existing)
- Load `seaHopper` from `graph-cache/sea`
- Load `ChokepointRegistry` from `graph-cache/sea/chokepoint_metadata.json`

#### 🔧 Create RoutingEngineRegistry (follows existing HK2 pattern)

```java
public class RoutingEngineRegistry {
    private final GraphHopper roadHopper;
    private final GraphHopper seaHopper;

    public GraphHopper getHopper(RoutingMode mode) {
        return switch (mode) {
            case ROAD -> roadHopper;
            case SEA -> seaHopper;
        };
    }
}

// Create as separate file: com.dynop.graphhopper.matrix.api.RoutingMode.java
package com.dynop.graphhopper.matrix.api;

public enum RoutingMode { ROAD, SEA }
```

#### 🏷️ Create ChokepointRegistry

```java
public class ChokepointRegistry {
    private final Map<String, Chokepoint> chokepoints;
    
    public static ChokepointRegistry loadFrom(Path metadataFile) {
        // Load from graph-cache/sea/chokepoint_metadata.json
    }
    
    public Chokepoint getChokepoint(String id) {
        return chokepoints.get(id);
    }
    
    public Set<Integer> getExcludedNodeIds(List<String> excludedChokepoints) {
        return excludedChokepoints.stream()
            .map(this::getChokepoint)
            .filter(Objects::nonNull)
            .flatMap(cp -> cp.getNodeIds().stream())
            .collect(Collectors.toSet());
    }
}
```

#### 🔌 Bind in MatrixBundle (extend existing)

```java
// In MatrixBundle.run(), wrap bindings in AbstractBinder (follows existing pattern):
environment.jersey().register(new AbstractBinder() {
    @Override
    protected void configure() {
        bind(routingEngineRegistry).to(RoutingEngineRegistry.class);
        bind(chokepointRegistry).to(ChokepointRegistry.class);
        bind(executorService)
                .to(ExecutorService.class)
                .named(MatrixResourceBindings.EXECUTOR_BINDING);
        bind(metrics).to(MetricRegistry.class);
    }
});
```

**Important:** The existing codebase uses `AbstractBinder` from `org.glassfish.hk2.utilities.binding`.
Do NOT use the simplified `bind()` syntax outside of an `AbstractBinder.configure()` method.

---

### 4️⃣ Matrix API Extension

#### 📝 Extend existing `MatrixRequest`

```java
// Add to MatrixRequest.java
@JsonProperty(value = "mode", defaultValue = "road")
private final RoutingMode mode;  // ROAD or SEA

@JsonProperty(value = "excluded_chokepoints")
private final List<String> excludedChokepoints;  // e.g., ["SUEZ", "PANAMA"]

@JsonProperty(value = "validate_coordinates", defaultValue = "true")
private final boolean validateCoordinates;  // Skip validation for pre-validated ports
```

#### 🔌 Extend existing `MatrixResource`

```java
// Inject RoutingEngineRegistry and ChokepointRegistry
@Inject
public MatrixResource(RoutingEngineRegistry registry,
                      ChokepointRegistry chokepointRegistry,
                      @Named(MatrixResourceBindings.EXECUTOR_BINDING) ExecutorService executorService,
                      MetricRegistry metrics) {
    // Use registry.getHopper(request.getMode()) in compute()
    // Apply chokepoint exclusions for SEA mode
}

// In compute method for SEA mode:
if (request.getMode() == RoutingMode.SEA) {
    EdgeFilter edgeFilter = new ChokepointAwareEdgeFilter(
        request.getExcludedChokepoints(), 
        chokepointRegistry
    );
    // Pass edgeFilter to routing algorithm
}
```

#### ✅ Ensure

- Same response schema (`MatrixResponse`)
- Same performance guarantees (thread pool, CH fallback)
- Default `mode=road` for backward compatibility
- **Excluded chokepoints included in response metadata**
- **Excluded chokepoints included in cache key computation**

---

### 4.5️⃣ Port Coordinate Handling (Runtime) — Two-Stage Snapping

Sea routing requires a **two-stage snapping process** to ensure all maritime legs are routed between **real UN/LOCODE seaports**, not arbitrary coordinates.

```
User Coordinate → [Stage 1: Port Snapping] → POL/POD (UN/LOCODE) → [Stage 2: Sea-Node Snapping] → Sea Graph
```

---

#### 🚢 Stage 1: Port Snapping (POL / POD Selection)

> **Core Principle:** Ocean routing is ALWAYS port-to-port.  
> User-provided coordinates must be snapped to a valid **UN/LOCODE seaport** before any sea routing occurs.

##### 📦 Reference Data Required

Create/import a `ports` table (authoritative source):

| Column | Type | Description |
|--------|------|-------------|
| `unlocode` | VARCHAR(5) | UN/LOCODE identifier (e.g., `NLRTM`) |
| `name` | VARCHAR | Port name |
| `country` | VARCHAR(2) | ISO country code |
| `lat` | DOUBLE | Port latitude |
| `lon` | DOUBLE | Port longitude |
| `geom` | GEOMETRY | Point geometry (SRID 4326) |
| `active` | BOOLEAN | Exclude deprecated ports |

##### 🔍 Snapping Algorithm (Applies to BOTH POL and POD)

The **same snapping logic** is used for both Port of Loading (POL) and Port of Discharge (POD).  
Both endpoints MUST resolve to valid UN/LOCODE seaports using identical rules.

```java
public class UnlocodePortSnapper {
    private static final double MAX_PORT_SNAP_DISTANCE_KM = 300.0;
    private final PortRepository portRepository;  // Backed by ports table
    
    /**
     * Snap user coordinate to nearest valid UN/LOCODE seaport.
     * Uses great-circle (Haversine) distance for nearest-neighbor search.
     * 
     * This method is used for BOTH:
     *   - Port of Loading (POL) — origin side
     *   - Port of Discharge (POD) — destination side
     * 
     * The snapping rules are IDENTICAL for both endpoints.
     */
    public PortSnapResult snapToPort(double lat, double lon, PortRole role) {
        // Spatial nearest-neighbor query against ports.geom
        PortCandidate nearest = portRepository.findNearestActiveSeaport(lat, lon);
        
        if (nearest == null) {
            throw new PortSnapException("NO_SEAPORT_FOUND", lat, lon, role);
        }
        
        double distanceKm = haversineDistanceKm(lat, lon, nearest.getLat(), nearest.getLon());
        
        // CRITICAL: Enforce snap distance guardrail (same threshold for POL and POD)
        if (distanceKm > MAX_PORT_SNAP_DISTANCE_KM) {
            throw new PortSnapException(
                "NO_SEAPORT_WITHIN_RANGE",
                lat, lon,
                nearest.getUnlocode(),
                distanceKm,
                role  // POL or POD
            );
        }
        
        return new PortSnapResult(
            nearest.getUnlocode(),
            nearest.getName(),
            nearest.getLat(),
            nearest.getLon(),
            distanceKm,
            "NEAREST_SEAPORT",  // snap method
            role                // POL or POD
        );
    }
}

// Port role enum for clarity
public enum PortRole {
    PORT_OF_LOADING,    // POL - origin side
    PORT_OF_DISCHARGE   // POD - destination side
}
```

##### 📍 POL / POD Assignment (Same UN/LOCODE Mapping)

Both POL and POD are resolved using the **identical** `UnlocodePortSnapper`:

```java
// Origin → POL
PortSnapResult pol = portSnapper.snapToPort(originLat, originLon, PortRole.PORT_OF_LOADING);

// Destination → POD (SAME snapping logic, SAME UN/LOCODE table)
PortSnapResult pod = portSnapper.snapToPort(destLat, destLon, PortRole.PORT_OF_DISCHARGE);
```

- Origin-side snapped port → **Port of Loading (POL)** → UN/LOCODE
- Destination-side snapped port → **Port of Discharge (POD)** → UN/LOCODE

These values are **immutable** for the remainder of the ocean routing workflow.

##### ✅ Validation Rules (Apply Equally to POL and POD)

| Rule | Behavior | Applies To |
|------|----------|------------|
| Inland coordinate >300 km from sea | Return `NO_SEAPORT_WITHIN_RANGE` error | POL & POD |
| Coastal factory/warehouse | Snap to correct nearby UN/LOCODE port | POL & POD |
| Determinism | Same input coordinates ALWAYS snap to same UN/LOCODE | POL & POD |
| UN/LOCODE required | Both endpoints MUST resolve to valid UN/LOCODE entries | POL & POD |
| POL ≠ POD | Enforced unless domestic coastal shipping is explicitly allowed | Both |

##### 📋 Required Metadata (Response/Logs)

Always include snapping metadata for debugging and auditability:

```json
{
  "port_of_loading": {
    "unlocode": "NLRTM",
    "name": "Rotterdam",
    "original_coordinates": { "lat": 51.95, "lon": 4.12 },
    "snap_distance_km": 15.3,
    "snap_method": "NEAREST_SEAPORT"
  },
  "port_of_discharge": {
    "unlocode": "CNSHA",
    "name": "Shanghai",
    "original_coordinates": { "lat": 31.23, "lon": 121.47 },
    "snap_distance_km": 8.7,
    "snap_method": "NEAREST_SEAPORT"
  }
}
```

---

#### 🌊 Stage 2: Sea-Node Snapping (Graph Entry Points)

After POL/POD are determined, snap port coordinates to the sea-lane graph.

##### 📍 Snapping to Graph

```java
public class SeaNodeSnapper {
    private final LocationIndex locationIndex;  // GraphHopper's spatial index
    private final double maxSnapDistanceMeters = 300_000; // ~300km tolerance
    
    /**
     * Snap port coordinates to nearest sea-lane graph edge.
     * Uses GraphHopper's LocationIndex.findClosest() mechanism.
     */
    public SnapResult snapToGraph(double lat, double lon) {
        Snap snap = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        
        if (!snap.isValid() || snap.getQueryDistance() > maxSnapDistanceMeters) {
            throw new RoutingException("Port too far from sea-lane network: " + 
                snap.getQueryDistance() + "m");
        }
        
        return new SnapResult(snap.getSnappedPoint(), snap.getClosestEdge());
    }
}
```

##### ⚠️ Important Constraints

- Ports MUST NOT be added as permanent nodes to the sea-lane graph
- Port→sea-node snapping is handled via GraphHopper's `QueryGraph` mechanism
- POL/POD coordinates from Stage 1 are used as inputs to Stage 2

---

#### ✅ Pre-Routing Validation

Validate coordinates before port snapping:

```java
public class PortCoordinateValidator {
    private final Geometry landGeometry;  // Natural Earth land polygons
    private final GeometryFactory gf = new GeometryFactory();
    
    public ValidationResult validate(double lat, double lon) {
        Point point = gf.createPoint(new Coordinate(lon, lat));
        
        if (landGeometry.contains(point)) {
            return ValidationResult.onLand(lat, lon);
        }
        if (lat < -80 || lat > 80) {
            return ValidationResult.polarRegion(lat, lon);
        }
        return ValidationResult.valid();
    }
}
```

---

#### 🔌 Integration in MatrixResource

```java
// In MatrixResource, before routing (sea mode only):
if (request.getMode() == RoutingMode.SEA) {
    // Stage 1: Snap ALL points to UN/LOCODE ports (same logic for sources and targets)
    List<PortSnapResult> snappedPorts = new ArrayList<>();
    
    for (int i = 0; i < request.getPoints().size(); i++) {
        GHPoint point = request.getPoints().get(i);
        
        // Determine role based on position (for error messages and logging)
        PortRole role = request.getSources().contains(i) 
            ? PortRole.PORT_OF_LOADING 
            : PortRole.PORT_OF_DISCHARGE;
        
        // Optional: validate coordinates first
        if (request.isValidateCoordinates()) {
            ValidationResult validation = portValidator.validate(point.lat, point.lon);
            if (!validation.isValid()) {
                return MatrixResponse.failure(validation.getError());
            }
        }
        
        // Snap to nearest UN/LOCODE seaport (SAME method for POL and POD)
        PortSnapResult portSnap = portSnapper.snapToPort(point.lat, point.lon, role);
        snappedPorts.add(portSnap);
    }
    
    // Validate POL ≠ POD for each origin-destination pair (if required)
    if (!request.isAllowSamePolPod()) {
        for (int srcIdx : request.getSources()) {
            for (int tgtIdx : request.getTargets()) {
                if (snappedPorts.get(srcIdx).getUnlocode()
                        .equals(snappedPorts.get(tgtIdx).getUnlocode())) {
                    return MatrixResponse.failure("POL_EQUALS_POD", 
                        snappedPorts.get(srcIdx).getUnlocode());
                }
            }
        }
    }
    
    // Use snapped port coordinates for routing
    List<GHPoint> routingPoints = snappedPorts.stream()
        .map(p -> new GHPoint(p.getLat(), p.getLon()))
        .collect(Collectors.toList());
    
    // Create chokepoint-aware edge filter
    EdgeFilter edgeFilter = new ChokepointAwareEdgeFilter(
        request.getExcludedChokepoints(),
        chokepointRegistry
    );
    
    // Stage 2: Sea-node snapping happens via GraphHopper's QueryGraph
    // ... proceed with matrix computation using routingPoints and edgeFilter
}
```

---

#### ❌ Error Responses

| Condition | HTTP Status | Error Code | Details | Applies To |
|-----------|-------------|------------|---------|------------|
| Port on land | 400 | `COORDINATE_ON_LAND` | Input lat/lon | POL & POD |
| Polar region | 400 | `POLAR_REGION_UNSUPPORTED` | Latitude outside −80° to +80° | POL & POD |
| No port within range | 400 | `NO_SEAPORT_WITHIN_RANGE` | Input coordinates, nearest port, distance, role | POL & POD |
| POL equals POD | 400 | `POL_EQUALS_POD` | UN/LOCODE of duplicate port | Route pair |
| Too far from graph | 400 | `GRAPH_SNAP_FAILED` | Port coordinates, snap distance | POL & POD |
| Chokepoint exclusion leaves no route | 400 | `NO_ROUTE_AVAILABLE` | Excluded chokepoints, origin, destination | Route pair |

---

#### ⚙️ Configuration

```yaml
# In sea-config.yml
sea:
  port_snapping:
    max_snap_distance_km: 300       # Maximum distance to snap user coordinate to port
    require_unlocode: true          # Only UN/LOCODE ports allowed
    allow_same_pol_pod: false       # POL must differ from POD (except coastal)
  
  graph_snapping:
    max_snap_distance_meters: 300000  # Port-to-graph snap tolerance
    
  validate_coordinates: true        # Can be disabled for pre-validated ports
  
  chokepoints:
    metadata_file: chokepoint_metadata.json  # Relative to graph-cache/sea/
```

---

#### 🏗️ Architecture Notes

- **Ports table**: Must be populated from authoritative UN/LOCODE source before sea routing is enabled
- **Land geometry persistence**: The `SeaLaneGraphBuilder` exports land geometry to `graph-cache/sea/land_geometry.wkb` for runtime validation
- **Chokepoint metadata**: Persisted to `graph-cache/sea/chokepoint_metadata.json` at build time
- **Lazy loading**: Load land geometry only when first sea routing request is received
- **No runtime dependency on OSM**: The persisted geometry is sufficient
- **Caching**: POL/POD snap results can be cached by input coordinate hash for performance
- **Cache key must include**: `mode`, `excluded_chokepoints`, `profile`

---

#### 🚫 Forbidden

- ❌ Sea routing from raw user coordinates (must go through port snapping)
- ❌ Snapping to arbitrary coastline points
- ❌ Silent fallback to detour-factor logic when snapping fails
- ❌ Using non-UN/LOCODE maritime points as POL/POD
- ❌ Ignoring excluded chokepoints silently

---

### 5️⃣ Deterministic Lead-Time Model (Sea)

After distance calculation:

```
sailing_hours = distance_nm / VESSEL_SPEED_KNOTS

total_lead_time_days =
  sailing_hours / 24
  + origin_port_dwell_days
  + destination_port_dwell_days
```

#### ⚙️ Configuration Approach

- **Option 1:** Global defaults in `sea-config.yml` (e.g., `sea.default_port_dwell_days: 2`)
- **Option 2:** Per-request parameters in `MatrixRequest` (future enhancement)
- **Option 3:** Port-specific lookup from UN/LOCODE data (future enhancement)

**Recommended:** Start with global config defaults, make them overridable later.

#### ⚠️ Constraints

- Configurable per scenario
- No calendars, no stochastic behavior
- Must be deterministic for identical inputs

---

### 6️⃣ Build Artifacts & Runtime Metrics (ENTERPRISE READINESS)

#### 📦 Build Artifacts (REQUIRED)

The `SeaLaneGraphBuilder` MUST emit the following artifacts to `graph-cache/sea/`:

| Artifact | Description |
|----------|-------------|
| `sea_graph_version` | Stable hash of graph (for cache invalidation) |
| `build_summary.json` | Full build metadata |
| `chokepoint_metadata.json` | Chokepoint definitions with node IDs |

**`build_summary.json` contents:**

```json
{
  "sea_graph_version": "sha256:abc123...",
  "node_count": 12500,
  "edge_count": 75000,
  "connected_component_count": 1,
  "largest_component_size": 12500,
  "build_duration_ms": 45000,
  "waypoint_grid_step_degrees": 5.0,
  "chokepoint_densification_step_degrees": 0.5,
  "land_mask_source": "ne_50m_land.geojson",
  "graphhopper_version": "11.0",
  "build_timestamp": "2025-12-24T10:30:00Z"
}
```

These MUST be logged at INFO level and written to disk.

#### 📊 Runtime Metrics (REQUIRED)

Expose via Dropwizard MetricRegistry:

| Metric Type | Name | Description |
|-------------|------|-------------|
| Histogram | `sea.port_snap_distance_km` | Distance from input to snapped port |
| Timer | `sea.routing_latency` | End-to-end sea routing time |
| Counter | `sea.routing_failures` | Failed sea routing requests |
| Counter | `sea.routing_by_scenario` | Requests tagged by excluded chokepoints |
| Gauge | `sea.graph_node_count` | Current graph size |

**Example registration:**

```java
@Inject
public MatrixResource(MetricRegistry metrics, ...) {
    this.portSnapHistogram = metrics.histogram("sea.port_snap_distance_km");
    this.routingTimer = metrics.timer("sea.routing_latency");
    this.routingFailures = metrics.counter("sea.routing_failures");
}
```

---

### 7️⃣ Automated Testing (REQUIRED)

#### 🧪 Unit Tests

- Waypoint generation
- Land filtering (using Natural Earth geometry)
- Edge intersection rejection
- Graph version hashing
- **Antimeridian handling:**
  - Distance calculation across ±180° meridian is correct
  - KNN finds neighbors across dateline (e.g., +179° → −179°)
  - No duplicate nodes created for wrapped coordinates
- **Chokepoint densification:**
  - Dense grid generated within radius of chokepoint
  - Chokepoint connected only to local dense waypoints
  - Dense waypoints validated against land geometry
- **Chokepoint handling:**
  - Chokepoint nodes correctly tagged during build
  - ChokepointRegistry loads metadata correctly
  - ChokepointAwareEdgeFilter excludes correct nodes
  - Empty exclusion list allows all edges
- **Port coordinate validation:**
  - Port on land returns validation error
  - Port in water passes validation
  - Port in polar region (lat > 80° or < -80°) returns error
- **Port snapping (Stage 1 - POL/POD):**
  - User coordinate snaps to nearest UN/LOCODE seaport
  - Snap distance >300km returns `NO_SEAPORT_WITHIN_RANGE` error
  - Same input coordinates always return same UN/LOCODE (determinism)
  - POL ≠ POD enforced for non-coastal routes
  - Snap metadata includes: unlocode, name, distance, method
- **Sea-node snapping (Stage 2 - Graph):**
  - Port at exact waypoint snaps correctly
  - Port between waypoints snaps to nearest edge
  - Port 500km from any node returns snap error (exceeds 300km threshold)
  - Snap distance is configurable

#### 🔗 Integration Tests

**Note:** Integration test infrastructure needs to be created as part of this implementation.
Follow the pattern in DEVELOPER_GUIDE.md for testing with custom OSM data.

**Test cases:**

1. Build sea graph end-to-end
2. **Validate connected components (build must succeed)**
3. Load via GraphHopper
4. Route:
   - Shanghai → Rotterdam (via Suez)
   - US East Coast → Asia (via Panama)
   - **Shanghai → Los Angeles (trans-Pacific, crosses antimeridian)**
   - **Gibraltar → Mediterranean (chokepoint routing)**
   - **Shanghai → Rotterdam with `excluded_chokepoints=["SUEZ"]`** (via Cape)
5. Assert:
   - Route exists
   - No land crossing
   - Deterministic distance
   - **Trans-Pacific route does NOT detour via Suez/Panama**
   - **Chokepoint routes use densified local waypoints**
   - **Suez exclusion routes via Cape of Good Hope**
   - **Suez exclusion increases distance significantly (>5000 nm difference)**

#### ⚡ Performance Test

- 1k × 1k sea matrix query
- Ensure acceptable latency
- Test with various chokepoint exclusion scenarios

#### 🔗 Connectivity Tests (MANDATORY)

These tests MUST pass or the build is considered failed:

| Test | Route | Condition | Expected |
|------|-------|-----------|----------|
| Pacific connectivity | Tokyo ↔ Los Angeles | None | Route exists, crosses dateline |
| Baseline Asia-Europe | Shanghai ↔ Rotterdam | None | Route via Suez |
| Suez closure | Shanghai ↔ Rotterdam | `excluded=["SUEZ"]` | Route via Cape, +5000nm |
| Invalid exclusion | Shanghai ↔ Rotterdam | `excluded=["SUEZ","CAPE_GOOD_HOPE"]` | `NO_ROUTE_AVAILABLE` error |

---

## 📚 Documentation Deliverables (MANDATORY)

### 📘 Business Documentation

**Audience:** Supply chain managers

**Explain:**
- What sea routing does
- What it is used for (network design, scenarios)
- What it is NOT (live vessel tracking)
- How lead times are calculated
- Assumptions & limitations
- **How chokepoint scenarios work** (Suez closure, etc.)

### 🧑‍💻 Developer Guide

**Explain:**
- How sea routing fits with truck routing
- How to rebuild the sea graph
- Config parameters (waypoint step, dwell times)
- How to add new chokepoints
- **How to use `excluded_chokepoints` parameter**
- How to debug routing issues
- **How to interpret build artifacts and metrics**

### 🧠 Technical Architecture Doc

**Include:**
- Component diagram (road vs sea graph)
- GraphHopper usage rationale
- Why CH is not used for sea
- Determinism & reproducibility guarantees
- **Chokepoint exclusion architecture**
- **Global connectivity validation approach**
- Caching strategy (future)
- **Metrics and observability design**

**All docs must be committed under `/docs`.**

---

## ✅ Validation Scenarios (MUST PASS)

1. ✅ Truck routing unchanged
2. ✅ Sea routing selectable via API (`mode=sea`)
3. ✅ Identical inputs → identical outputs
4. ✅ Graph rebuild invalidates cached results
5. ✅ No runtime dependency on land geometry
6. ✅ **Global connectivity validated at build time**
7. ✅ **Chokepoint exclusion works at query time**
8. ✅ **Suez exclusion routes via Cape of Good Hope**
9. ✅ **Build artifacts emitted and metrics exposed**

---

## 🚫 Forbidden

- ❌ Python runtime
- ❌ Standalone Dijkstra
- ❌ Mixed road+sea graph
- ❌ Timetables or live data
- ❌ Multiple routing engines
- ❌ Converting Natural Earth into a routing graph
- ❌ Single point of failure chokepoints (must have alternatives)
- ❌ Silent fallback when chokepoint exclusion is requested
- ❌ Skipping connectivity validation at build time

---

## 📋 Current Codebase Reference

Key files to understand before implementation:

| File | Purpose |
|------|--------|
| `MatrixServerApplication.java` | Dropwizard entry point, bundles registration |
| `MatrixServerConfiguration.java` | Extends `GraphHopperServerConfiguration` |
| `MatrixBundle.java` | HK2 injection setup, executor management |
| `MatrixGraphHopperProvider.java` | Bridge interface to access shared GraphHopper instance |
| `MatrixResource.java` | Matrix API endpoint at `/custom/matrix` |
| `MatrixRequest.java` | Request DTO with `profile`, `points`, etc. |
| `MatrixResponse.java` | Response DTO with `distances[][]`, `times[][]`, `failures[]` |
| `devtools/graphhopper-build/config.yml` | GraphHopper + Dropwizard config (road routing) |

### 🆕 New Files to Create

| File | Purpose |
|------|--------|
| `RoutingEngineRegistry.java` | Holds road and sea GraphHopper instances |
| `RoutingMode.java` | Enum: `ROAD`, `SEA` |
| `ChokepointRegistry.java` | Loads and manages chokepoint metadata |
| `Chokepoint.java` | Domain object for chokepoint |
| `ChokepointAwareEdgeFilter.java` | EdgeFilter that excludes chokepoint nodes |
| `SeaLaneGraphBuilder.java` | Offline CLI to build sea graph |
| `UnlocodePortSnapper.java` | Snaps coordinates to UN/LOCODE ports |
| `SeaNodeSnapper.java` | Snaps ports to sea graph nodes |
| `devtools/graphhopper-build/sea-config.yml` | Sea routing config |
| `devtools/graphhopper-build/dynop-ship.json` | Ship custom model |

---

## 🔄 GraphHopper Upgrade Considerations

### 🔍 Current Deep Coupling (Risk Assessment)

| API Category | Classes Used | Risk Level |
|--------------|--------------|------------|
| Core Graph Storage | `BaseGraph`, `RoutingCHGraph`, `LocationIndex`, `Snap` | 🔴 High |
| Routing Algorithms | `RoutingAlgorithm`, `RoutingAlgorithmFactory`, `CHRoutingAlgorithmFactory`, `LMRoutingAlgorithmFactory` | 🔴 High |
| Query Graph | `QueryGraph`, `QueryRoutingCHGraph` | 🔴 High |
| Configuration | `Profile`, `PMap`, `AlgorithmOptions` | 🟡 Medium |
| Dropwizard Integration | `GraphHopperBundle`, `GraphHopperServerConfiguration` | 🟡 Medium |

### ➕ Ocean-Lane Implementation Adds

| Component | Additional Coupling | Notes |
|-----------|-------------------|-------|
| `SeaLaneGraphBuilder` | `GraphHopperStorage`, `BaseGraph.create()` | 🔴 Uses internal graph construction APIs |
| `RoutingEngineRegistry` | `GraphHopper` instances only | 🟢 Thin wrapper |
| `ChokepointRegistry` | None (JSON metadata) | 🟢 No GH coupling |
| `ChokepointAwareEdgeFilter` | `EdgeFilter`, `EdgeIteratorState` | 🟡 Medium |
| Ship profile | YAML config only | 🟢 No code coupling |

### ⏱️ Upgrade Effort Estimates

| Scenario | Effort | Action Required |
|----------|--------|-----------------|
| Patch (11.0.x) | 🟢 Low | Test, rebuild graph cache |
| Minor (11.x) | 🟡 Medium | Review changelog, may need config updates |
| Major (12.x+) | 🔴 High | 2-4 days refactoring, rebuild all graphs |

### 🛡️ Recommended Abstraction Wrappers

To reduce future upgrade pain, consider wrapping deep-coupled classes:

```java
// Wrap routing algorithm creation
public interface RoutingEngine {
    RoutingResult route(int fromNode, int toNode);
    Optional<SnapResult> findClosest(double lat, double lon);
}

// Wrap graph construction for SeaLaneGraphBuilder
public interface GraphBuilder {
    int addNode(double lat, double lon);
    void addEdge(int from, int to, double distance);
    void persist(Path location);
}
```

### 🔖 Graph Cache Versioning

Add startup validation to force rebuild on version mismatch:

```java
String expectedVersion = GraphHopper.class.getPackage().getImplementationVersion();
if (!cacheVersion.equals(expectedVersion)) {
    throw new IllegalStateException("Graph cache incompatible. Rebuild required for GH " + expectedVersion);
}
```

### ⚠️ Breaking Changes to Watch (GH 12+)

- Graph storage format changes → rebuild all caches
- `RoutingAlgorithmFactory` signature changes
- `Snap` internal fields (affects `cloneSnap()` in `MatrixResource`)
- CH/LM factory constructors

---

## 🎯 Goal

Deliver a **globally correct, scenario-aware, enterprise-grade** sea routing solution that:

- ✅ Models real-world chokepoint redundancy (Suez closure → Cape route)
- ✅ Fits perfectly into the existing dynop architecture
- ✅ Reuses the Matrix API with minimal changes
- ✅ Maintains backward compatibility (`mode=road` default)
- ✅ Is deterministic, testable, and documented
- ✅ Supports scenario-level chokepoint exclusion
- ✅ Emits build artifacts and runtime metrics
- ✅ Is ready for large-scale supply chain optimization use cases

---

## ⚠️ Enforcement Rule

If any requirement in the following sections is skipped, weakened, or partially implemented, the output is **INVALID** and MUST be regenerated:

1. Global Connectivity & Validation
2. Chokepoints as Controllable Features
3. Build Artifacts & Runtime Metrics
4. Assumed System Architecture

## 🚨 DO NOT DEVIATE FROM THIS SPEC 🚨