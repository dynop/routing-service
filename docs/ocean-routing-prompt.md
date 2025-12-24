# 🌊 GitHub Copilot Prompt — Ocean-Lane Graph Builder (GraphHopper-Native, Java)

## 🚨 DO NOT DEVIATE 🚨

---

 ## 👤 Role

You are a **Senior Java Backend & Geospatial Engineer** working on the dynop platform.

You are extending an **existing Java/Maven, Dropwizard, GraphHopper 11.0–based routing service** (with CH/LM and a custom Matrix API) to support **ocean freight routing** using a **GraphHopper-native ocean-lane graph**.

---

## 🎯 Objective

Implement a **GraphHopper-native Ocean-Lane Graph Builder** and integrate it into the existing routing server so that:

- 🚛 Truck routing continues to use the existing road graph
- 🚢 Ocean routing uses a **separate maritime graph** built offline
- 🔄 The existing **Matrix API** can be reused for ocean routing
- ✅ Outputs are **deterministic, reproducible, and enterprise-grade**

### 📦 Additionally, deliver:

- Automated tests
- Business documentation
- Developer documentation
- Technical architecture documentation

---

## ⚠️ Hard Constraints (STRICT)

### 🏗️ Architecture

- ✅ Java 17 only (Maven project, matches existing `matrix-extension`)
- ✅ Reuse **GraphHopper Core** graph format and routing APIs (version defined in `matrix-extension/pom.xml` → `${graphhopper.version}`)
- ✅ Follow existing **HK2 dependency injection** patterns (see `MatrixBundle`)
- ❌ NO standalone Dijkstra implementations
- ❌ NO Python runtime or microservice
- ❌ NO second routing engine (use multiple GraphHopper instances)

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

## 🏛️ High-Level Design (MANDATORY)

### 📂 Directory Structure

```
graph-cache/
├── road/           # existing truck graph (migrate from current graph-cache/)
└── ocean/          # new maritime graph
```

#### ⚠️ Migration Checklist (One-Time)

The current graph cache is at `devtools/graphhopper-build/graph-cache/` with files in root.

Before implementing ocean routing:

1. Create `graph-cache/road/` subdirectory
2. Move all existing cache files into `graph-cache/road/`
3. Update `devtools/graphhopper-build/config.yml`:
   - Change: `graph.location: /app/graph-cache` → `graph.location: /app/graph-cache/road`
4. Rebuild road graph to verify migration
5. Create empty `graph-cache/ocean/` for maritime graph

### 🏗️ Application Architecture

```
Dropwizard App (MatrixServerApplication)
├── RoutingEngineRegistry         # NEW: holds both hoppers
│   ├── GraphHopper roadHopper    # profile: truck
│   └── GraphHopper oceanHopper   # profile: ship
├── MatrixBundle                  # existing, extended
└── MatrixResource                # existing, extended with mode param
```

### 🔀 Mode vs Profile (IMPORTANT)

- **mode** → selects which GraphHopper instance (`road` or `ocean`)
- **profile** → selects routing profile within that hopper (`truck`, `ship`)

### 🚀 Routing Behavior

- **Ocean routing uses GraphHopper runtime** (same as road)
- **No CH required for ocean graph** (use `enableFallback: true`)
- Mode selection happens at API layer via `MatrixRequest.mode`

---

## ✅ Implementation Tasks (MANDATORY ORDER)

### 1️⃣ Ocean-Lane Graph Builder (Offline, Java)

Create a CLI/Job module: `OceanLaneGraphBuilder`

**Responsibilities:**
- Generate a **synthetic maritime graph**
- Persist it in **GraphHopperStorage** format

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

3. **🚨 CRITICAL: Chokepoint Densification**
   - Coarse 5° grid is insufficient near narrow straits
   - Locally **densify the waypoint grid around each chokepoint**:
     - Smaller step size: 0.5°–1° within 2–3° radius of chokepoint
     - Connect chokepoints only to these dense local waypoints
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

9. **Persist to `graph-cache/ocean/`**

10. **Compute and store a `ocean_graph_version` hash**

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

// Usage: densifyAroundChokepoint(35.94, -5.61, 3.0, 0.5) // Gibraltar
```

---

### 2️⃣ Ocean Routing Profile (GraphHopper)

Create a GraphHopper profile in ocean config:

```yaml
# devtools/graphhopper-build/ocean-config.yml
graphhopper:
  graph.location: /app/graph-cache/ocean
  
  # Minimal encoded values for ocean graph (no road restrictions needed)
  graph.encoded_values: car_access, car_average_speed
  
  profiles:
    - name: ship
      custom_model_files: [dynop-ship.json]
      turn_costs: false

  # NO CH for ocean (use fallback routing)
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
- Load `oceanHopper` from `graph-cache/ocean`

#### 🔧 Create RoutingEngineRegistry (follows existing HK2 pattern)

```java
public class RoutingEngineRegistry {
    private final GraphHopper roadHopper;
    private final GraphHopper oceanHopper;

    public GraphHopper getHopper(RoutingMode mode) {
        return switch (mode) {
            case ROAD -> roadHopper;
            case OCEAN -> oceanHopper;
        };
    }
}

// Create as separate file: com.dynop.graphhopper.matrix.api.RoutingMode.java
package com.dynop.graphhopper.matrix.api;

public enum RoutingMode { ROAD, OCEAN }
```

#### 🔌 Bind in MatrixBundle (extend existing)

```java
// In MatrixBundle.run(), wrap bindings in AbstractBinder (follows existing pattern):
environment.jersey().register(new AbstractBinder() {
    @Override
    protected void configure() {
        bind(routingEngineRegistry).to(RoutingEngineRegistry.class);
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
private final RoutingMode mode;  // ROAD or OCEAN

@JsonProperty(value = "validate_coordinates", defaultValue = "true")
private final boolean validateCoordinates;  // Skip validation for pre-validated ports
```

#### 🔌 Extend existing `MatrixResource`

```java
// Inject RoutingEngineRegistry instead of single GraphHopper
// Note: Use @Named qualifier for ExecutorService (existing pattern)
@Inject
public MatrixResource(RoutingEngineRegistry registry,
                      @Named(MatrixResourceBindings.EXECUTOR_BINDING) ExecutorService executorService,
                      MetricRegistry metrics) {
    // Use registry.getHopper(request.getMode()) in compute()
    // Example: GraphHopper hopper = registry.getHopper(request.getMode());
}
```

#### ✅ Ensure

- Same response schema (`MatrixResponse`)
- Same performance guarantees (thread pool, CH fallback)
- Default `mode=road` for backward compatibility

---

### 4.5️⃣ Port Coordinate Handling (Runtime) — Two-Stage Snapping

Ocean routing requires a **two-stage snapping process** to ensure all maritime legs are routed between **real UN/LOCODE seaports**, not arbitrary coordinates.

```
User Coordinate → [Stage 1: Port Snapping] → POL/POD (UN/LOCODE) → [Stage 2: Sea-Node Snapping] → Ocean Graph
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

After POL/POD are determined, snap port coordinates to the ocean-lane graph.

##### 📍 Snapping to Graph

```java
public class SeaNodeSnapper {
    private final LocationIndex locationIndex;  // GraphHopper's spatial index
    private final double maxSnapDistanceMeters = 300_000; // ~300km tolerance
    
    /**
     * Snap port coordinates to nearest ocean-lane graph edge.
     * Uses GraphHopper's LocationIndex.findClosest() mechanism.
     */
    public SnapResult snapToGraph(double lat, double lon) {
        Snap snap = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        
        if (!snap.isValid() || snap.getQueryDistance() > maxSnapDistanceMeters) {
            throw new RoutingException("Port too far from ocean-lane network: " + 
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
// In MatrixResource, before routing (ocean mode only):
if (request.getMode() == RoutingMode.OCEAN) {
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
    
    // Stage 2: Sea-node snapping happens via GraphHopper's QueryGraph
    // ... proceed with matrix computation using routingPoints
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

---

#### ⚙️ Configuration

```yaml
# In ocean-config.yml
ocean:
  port_snapping:
    max_snap_distance_km: 300       # Maximum distance to snap user coordinate to port
    require_unlocode: true          # Only UN/LOCODE ports allowed
    allow_same_pol_pod: false       # POL must differ from POD (except coastal)
  
  graph_snapping:
    max_snap_distance_meters: 300000  # Port-to-graph snap tolerance
    
  validate_coordinates: true        # Can be disabled for pre-validated ports
```

---

#### 🏗️ Architecture Notes

- **Ports table**: Must be populated from authoritative UN/LOCODE source before ocean routing is enabled
- **Land geometry persistence**: The `OceanLaneGraphBuilder` exports land geometry to `graph-cache/ocean/land_geometry.wkb` for runtime validation
- **Lazy loading**: Load land geometry only when first ocean routing request is received
- **No runtime dependency on OSM**: The persisted geometry is sufficient
- **Caching**: POL/POD snap results can be cached by input coordinate hash for performance

---

#### 🚫 Forbidden

- ❌ Ocean routing from raw user coordinates (must go through port snapping)
- ❌ Snapping to arbitrary coastline points
- ❌ Silent fallback to detour-factor logic when snapping fails
- ❌ Using non-UN/LOCODE maritime points as POL/POD

---

### 5️⃣ Deterministic Lead-Time Model (Ocean)

After distance calculation:

```
sailing_hours = distance_nm / VESSEL_SPEED_KNOTS

total_lead_time_days =
  sailing_hours / 24
  + origin_port_dwell_days
  + destination_port_dwell_days
```

#### ⚙️ Configuration Approach

- **Option 1:** Global defaults in `ocean-config.yml` (e.g., `ocean.default_port_dwell_days: 2`)
- **Option 2:** Per-request parameters in `MatrixRequest` (future enhancement)
- **Option 3:** Port-specific lookup from UN/LOCODE data (future enhancement)

**Recommended:** Start with global config defaults, make them overridable later.

#### ⚠️ Constraints

- Configurable per scenario
- No calendars, no stochastic behavior
- Must be deterministic for identical inputs

---

### 6️⃣ Automated Testing (REQUIRED)

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

1. Build ocean graph end-to-end
2. Load via GraphHopper
3. Route:
   - Shanghai → Rotterdam (via Suez)
   - US East Coast → Asia (via Panama)
   - **Shanghai → Los Angeles (trans-Pacific, crosses antimeridian)**
   - **Gibraltar → Mediterranean (chokepoint routing)**
4. Assert:
   - Route exists
   - No land crossing
   - Deterministic distance
   - **Trans-Pacific route does NOT detour via Suez/Panama**
   - **Chokepoint routes use densified local waypoints**

#### ⚡ Performance Test

- 1k × 1k ocean matrix query
- Ensure acceptable latency

---

## 📚 Documentation Deliverables (MANDATORY)

### 📘 Business Documentation

**Audience:** Supply chain managers

**Explain:**
- What ocean routing does
- What it is used for (network design, scenarios)
- What it is NOT (live vessel tracking)
- How lead times are calculated
- Assumptions & limitations

### 🧑‍💻 Developer Guide

**Explain:**
- How ocean routing fits with truck routing
- How to rebuild the ocean graph
- Config parameters (waypoint step, dwell times)
- How to add new chokepoints
- How to debug routing issues

### 🧠 Technical Architecture Doc

**Include:**
- Component diagram (road vs ocean graph)
- GraphHopper usage rationale
- Why CH is not used for ocean
- Determinism & reproducibility guarantees
- Caching strategy (future)

**All docs must be committed under `/docs`.**

---

## ✅ Validation Scenarios (MUST PASS)

1. ✅ Truck routing unchanged
2. ✅ Ocean routing selectable via API
3. ✅ Identical inputs → identical outputs
4. ✅ Graph rebuild invalidates cached results
5. ✅ No runtime dependency on land geometry

---

## 🚫 Forbidden

- ❌ Python runtime
- ❌ Standalone Dijkstra
- ❌ Mixed road+ocean graph
- ❌ Timetables or live data
- ❌ Multiple routing engines

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
| `OceanLaneGraphBuilder` | `GraphHopperStorage`, `BaseGraph.create()` | 🔴 Uses internal graph construction APIs |
| `RoutingEngineRegistry` | `GraphHopper` instances only | 🟢 Thin wrapper |
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

// Wrap graph construction for OceanLaneGraphBuilder
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

Deliver a GraphHopper-native, enterprise-grade ocean routing solution that:

- ✅ Fits perfectly into the existing dynop architecture
- ✅ Reuses the Matrix API with minimal changes
- ✅ Maintains backward compatibility (`mode=road` default)
- ✅ Is deterministic, testable, and documented
- ✅ Is ready for large-scale optimization use cases

## 🚨 DO NOT DEVIATE FROM THIS SPEC 🚨