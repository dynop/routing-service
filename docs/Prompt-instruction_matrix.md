# Development Task — Custom GraphHopper Matrix API Extension (v5 — High Performance + Shared Pool)

## Role: Senior Java Engineer & Geospatial Architect

You are GitHub Copilot. Implement a **production-grade N×M Matrix API** directly inside the **GraphHopper web server**.

**Stack:**
- **Core:** GraphHopper Core 11.0 (Java)
- **Server:** Dropwizard (internal `graphhopper-web` module)
- **Routing:** Contraction Hierarchies (CH) with optional fallback to Flexible/LM
- **Performance:** Parallelized routing targeting **10,000+ routes/sec**

This prompt is the **single authoritative specification**. Follow it EXACTLY.

---

## 1. Architectural Change — Shared Worker Pool (CRITICAL)

Do **NOT** create an `ExecutorService` inside the HTTP resource method.

You MUST implement a **Shared Worker Pool** pattern:

1. The Thread Pool is created **once** at application startup (in a Bundle).
2. The Thread Pool is **registered** with Dropwizard Lifecycle using the `Managed` interface for clean startup/shutdown.
3. The Thread Pool is **injected** into the `MatrixResource` (constructor injection).
4. All matrix computations use this **shared ExecutorService**.

---

## 2. Deliverables

1. `MatrixRequest.java` (DTO)
2. `MatrixResponse.java` (DTO)
3. `MatrixResource.java` (JAX-RS Endpoint)
4. `MatrixBundle.java` (Dropwizard Bundle & Lifecycle Manager)
5. **JUnit 5 Tests**:
  - Unit tests
  - Integration tests
  - Concurrency tests
  - Performance tests (**JMH** benchmark class)

---

## 3. DTO Specifications

### A. MatrixRequest.java

Jackson-annotated POJO with:

- `List<List<Double>> points`  
  - Each entry is `[lat, lon]`.
- `List<Integer> sources` (optional)  
  - Indices into `points`. Default: all indices if omitted.
- `List<Integer> targets` (optional)  
  - Indices into `points`. Default: all indices if omitted.
- `String profile`  
  - Example: `"truck"`.
- `List<String> metrics`  
  - Allowed: `"distance"`, `"time"`.
- `boolean enableFallback` (default: `false`)  
  - If `true` and CH is not available for the profile, use LM/flexible routing via GraphHopper's modern algorithm factory.

**Validation rules:**

- `points` list must be non-empty.
- All `[lat, lon]` must be valid doubles.
- `metrics` must be a non-empty subset of `{"distance", "time"}`.

### B. MatrixResponse.java

Fields:

- `long[][] distances` (meters)
- `long[][] times` (milliseconds)
- `List<Integer> failures`  
  - Indices in `points` that could not be snapped to the graph.

**Semantics:**

- For any point index in `failures`, the corresponding row and/or column (depending on whether it appears in `sources` or `targets`) MUST be filled with `-1` in both `distances` and `times`.
- For unreachable routes (e.g. `ConnectionNotFoundException` or `!path.isFound()`), the corresponding element MUST be `-1`.

Make DTOs **immutable** (final fields, constructor-only, no setters).

---

## 4. MatrixBundle.java — Shared Executor & Wiring

Implement `MatrixBundle` as a `ConfiguredBundle<GraphHopperBundleConfiguration>`.

### 4.1 Shared GraphHopper Reference

- Introduce a tiny bridge interface (e.g. `MatrixGraphHopperProvider`) that extends/ wraps `GraphHopperBundleConfiguration` and exposes:
  - `GraphHopper requireGraphHopper();` – returns the already-started instance or throws if `GraphHopperBundle` has not finished wiring yet.
  - Optional `void setGraphHopper(GraphHopper graphHopper);` so the Dropwizard application can store the instance when `GraphHopperBundle` completes. Do **not** modify `GraphHopperBundle` itself; instead, have your server configuration (e.g. `GraphHopperServerConfiguration`) implement the provider interface and populate it via your application bootstrap logic right after `GraphHopperBundle` finishes.
- `MatrixBundle` must assert that the incoming configuration implements this provider interface and use it to retrieve the shared `GraphHopper` instance. No second GraphHopper must ever be created.
- The Dropwizard application must register `GraphHopperBundle` **before** `MatrixBundle` so the provider already contains the live `GraphHopper` when the matrix bundle runs.

### 4.2 Thread Pool Lifecycle

**Responsibilities:**

1. **Initialize phase (if needed):**
  - No-op or config-related setup.

2. **Run phase:**

  - Read pool size from configuration property `matrix.executor.pool_size` (add this to `GraphHopperServerConfiguration`). If unspecified, default to:

    ```java
    int poolSize = Runtime.getRuntime().availableProcessors();
    ```

  - Create a **fixed thread pool**:

    ```java
    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
    ```

  - Wrap it in a `Managed` implementation that:
    - Shuts down the pool on `stop()`.
    - Optionally logs on `start()`/`stop()`.

  - Register it with:

    ```java
    environment.lifecycle().manage(managedExecutorService);
    ```

  - Obtain `GraphHopper` instance from the provider interface:

    ```java
    MatrixGraphHopperProvider provider = (MatrixGraphHopperProvider) configuration;
    GraphHopper gh = provider.requireGraphHopper();
    ```

  - Obtain `MetricRegistry` from environment:

    ```java
    MetricRegistry metrics = environment.metrics();
    ```

  - Register the resource:

    ```java
    environment.jersey().register(new MatrixResource(gh, executorService, metrics));
    ```

---

## 5. MatrixResource.java — Core Logic

Annotate with:

```java
@Path("/custom/matrix")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
```

**Constructor arguments:**

- `GraphHopper graphHopper`
- `ExecutorService executorService`
- `MetricRegistry metrics`

**Inside constructor:**

- Create a Timer for request latency, e.g. `matrix.requests.latency`.
- Create a Meter for route throughput, e.g. `matrix.routes.per_second`.

### Step 1 — Validation

- Ensure the requested profile exists via `graphHopper.getProfile(profileName)` (throw HTTP 400 if `null`).
- Determine preparation availability using the data structures that actually exist in GraphHopper 11.0:

  ```java
  boolean chEnabled = graphHopper.getCHGraphs().containsKey(profileName);
  boolean lmEnabled = graphHopper.getLandmarks().containsKey(profileName);
  ```

- **Behavior:**
  - If `!chEnabled && !enableFallback` → return HTTP 400 with JSON error (e.g. "CH not available and fallback disabled").
  - If `!chEnabled && enableFallback` but `lmEnabled` is `false`, drop to flexible routing by creating a `RoutingAlgorithmFactorySimple`.
  - If the profile name is unknown, respond with HTTP 400 "Unknown profile" before any routing work starts.

### Step 2 — Derive Sources & Targets

- If `sources` is null or empty → use all indices `[0..points.size()-1]`.
- If `targets` is null or empty → use all indices `[0..points.size()-1]`.
- Compute:

  ```java
  int rows = sources.size();
  int cols = targets.size();
  ```

- Enforce maximum matrix size (e.g. 5000x5000):

  ```java
  int maxSize = 5000;
  if ((long) rows * (long) cols > (long) maxSize * (long) maxSize) {
    // HTTP 400: "Matrix too large"
  }
  ```

- Allocate:

  ```java
  long[][] distances = new long[rows][cols];
  long[][] times = new long[rows][cols];
  ```

### Step 3 — Bulk Snapping

- Get `LocationIndex` from `graphHopper`.
- Snap all points ONCE:

  ```java
  Snap[] snaps = new Snap[points.size()];
  for (int i = 0; i < points.size(); i++) {
    double lat = points.get(i).get(0);
    double lon = points.get(i).get(1);
    snaps[i] = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
  }
  ```

- Build failures list:

  ```java
  List<Integer> failures = new ArrayList<>();
  for (int i = 0; i < snaps.length; i++) {
    if (!snaps[i].isValid()) {
       failures.add(i);
    }
  }
  ```

- For each failed index that appears in `sources` or `targets`, pre-fill the corresponding row/column with `-1`.

### Step 4 — Routing Infrastructure (CH / LM)

- Look up the prepared graphs using the 11.0 APIs:

  ```java
  RoutingCHGraph chGraph = graphHopper.getCHGraphs().get(profileName);
  LandmarkStorage lmStorage = graphHopper.getLandmarks().get(profileName);
  Profile profile = graphHopper.getProfile(profileName);
  Weighting weighting = graphHopper.createWeighting(profile, new PMap());
  ``

- Build the algorithm factory per mode:
  - **CH:** `new CHRoutingAlgorithmFactory(chGraph)` (wrap with `QueryRoutingCHGraph` once you have a per-task `QueryGraph`).
  - **LM fallback:** `new LMRoutingAlgorithmFactory(lmStorage).setDefaultActiveLandmarks(graphHopper.getRouterConfig().getActiveLandmarkCount());`
  - **Pure flexible:** `new RoutingAlgorithmFactorySimple();`

- `AlgorithmOptions`/`PMap` are still required even though we avoid the legacy `GraphHopperAPI`. Reuse GraphHopper's builder utilities exactly like `Router` does: `AlgorithmOptions algoOpts = AlgorithmOptions.start(profile.getHints()).algorithm(Parameters.Algorithms.DIJKSTRA_BI).traversalMode(profile.isTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED).weighting(weighting).build();`

The objects above (`chGraph`, `lmStorage`, `weighting`, `algoOpts`) are reused inside the per-row tasks below.

### Step 5 — Parallel Execution (Shared Executor)

- **Granularity:** 1 Task = 1 origin row (one source → all targets).
- For each `rowIdx`:

  ```java
  Callable<Void> task = () -> {
    int sourcePointIndex = sources.get(rowIdx);
    Snap sourceSnap = snaps[sourcePointIndex];
    if (!sourceSnap.isValid()) {
       // row already -1, just return
       return null;
    }

    // Per-thread / per-task setup:
    int sourceNode = sourceSnap.getClosestNode();

     // CH vs LM selection:
     if (chEnabled) {
       QueryGraph baseQueryGraph = QueryGraph.create(graphHopper.getBaseGraph(), Collections.singletonList(sourceSnap));
       QueryRoutingCHGraph queryCHGraph = new QueryRoutingCHGraph(chGraph, baseQueryGraph);
       RoutingAlgorithm algo = new CHRoutingAlgorithmFactory(queryCHGraph).createAlgo(new PMap());
       routeRowWithAlgo(algo, snaps, sourceNode, targets, rowIdx, distances, times);
     } else {
       QueryGraph qg = QueryGraph.create(graphHopper.getBaseGraph(), Collections.singletonList(sourceSnap));
       RoutingAlgorithmFactory fallbackFactory = lmEnabled
            ? new LMRoutingAlgorithmFactory(lmStorage).setDefaultActiveLandmarks(graphHopper.getRouterConfig().getActiveLandmarkCount())
            : new RoutingAlgorithmFactorySimple();
       RoutingAlgorithm algo = fallbackFactory.createAlgo(qg, weighting, algoOpts);
       routeRowWithAlgo(algo, snaps, sourceNode, targets, rowIdx, distances, times);
     }

    return null;
  };
  ```

- Submit all tasks:

  ```java
  List<Callable<Void>> tasks = ...;
  List<Future<Void>> futures = executorService.invokeAll(tasks);
  // Handle exceptions from futures if needed
  ```

- **Thread safety rule:**  
  Each task must:
  - Create its own `QueryGraph`.
  - Create its own `RoutingAlgorithm`.
  - Only write to `distances[rowIdx][*]` and `times[rowIdx][*]`.

### Step 6 — Per-Target Routing Logic

Implement helper (pseudocode):

```java
private void routeRowWithAlgo(
   RoutingAlgorithm algo,
   Snap[] snaps,
   int sourceNode,
   List<Integer> targets,
   int rowIdx,
   long[][] distances,
   long[][] times
) {
   for (int colIdx = 0; colIdx < targets.size(); colIdx++) {
      int targetPointIndex = targets.get(colIdx);
      Snap targetSnap = snaps[targetPointIndex];

      if (!targetSnap.isValid()) {
        distances[rowIdx][colIdx] = -1;
        times[rowIdx][colIdx] = -1;
        continue;
      }

      int targetNode = targetSnap.getClosestNode();
      try {
        Path path = algo.calcPath(sourceNode, targetNode);
        if (!path.isFound()) {
           distances[rowIdx][colIdx] = -1;
           times[rowIdx][colIdx] = -1;
        } else {
           distances[rowIdx][colIdx] = Math.round(path.getDistance());
           times[rowIdx][colIdx] = path.getTime(); // ms
        }
      } catch (ConnectionNotFoundException e) {
        distances[rowIdx][colIdx] = -1;
        times[rowIdx][colIdx] = -1;
      }
   }
}
```

### Step 7 — Optional One-to-Many Optimization (CH Only)

For CH and very large target sets (e.g. `targets.size() > 1000`):

- You MAY implement a read-only one-to-many CH traversal to compute distances from `sourceNode` to many nodes in a single pass.
- Use only stable, public GraphHopper APIs.
- Do NOT modify CH graph or shortcuts.
- Build an in-memory map `nodeId → distance/time`, then fill row from that map.

If GraphHopper's public API does not provide a safe one-to-many mechanism, fall back to the per-target `calcPath` loop.

### Step 8 — Metrics & Response

- Wrap the entire matrix calculation in a Dropwizard `Timer.Context` to record latency.
- After computing the matrix, mark the `Meter` with `rows * cols` routes.
- Build `MatrixResponse` and return `Response.ok(matrixResponse).build()`.

---

## 6. Testing Requirements

### 1. MatrixResourceTest.java (Unit)

**Mock:**
- `GraphHopper`
- `LocationIndex`
- `RoutingAlgorithmFactory`
- `RoutingAlgorithm`
- `ExecutorService` (can use a direct executor for unit-level tests)

**Verify:**
- Snap failures populate `failures` and correctly fill rows/cols with `-1`.
- Matrix dimensions match `sources.size() x targets.size()`.
- CH missing + `enableFallback=false` → HTTP 400.

### 2. MatrixIT.java (Integration)

- Load `andorra.osm.pbf`.
- Configure profile `"truck"` with CH enabled.
- Start Dropwizard server with `MatrixBundle`.
- POST a small matrix request to `/custom/matrix`.

**Assert:**
- HTTP 200.
- Correct non-zero distance and time between known coordinates.
- HTTP 400 when using an unknown profile.

### 3. ConcurrencyTest.java

- Use a real or test GraphHopper instance.
- Simulate ~50 concurrent HTTP requests (e.g. using a test client or multi-threaded test).

**Assert:**
- No `RejectedExecutionException`.
- No deadlocks.
- Responses are correct and consistent.

### 4. MatrixBenchmark.java (JMH)

- Benchmark a 50x50 matrix:
  - 50*50 = 2500 routes per request.
- Target: > 10,000 route calculations per second on a reasonable test machine.
- Include small (10x10), medium (100x100), and large (1000x1000) scenarios.
- Record throughput and average latency.

---

## 7. Guardrails & Constraints

- **API Version:** Use GraphHopper 11.0 APIs (`RouterConfig`, `Profile`, `RoutingAlgorithmFactory`).  
  Avoid the legacy `GraphHopperAPI` entry points; using `AlgorithmOptions` with `PMap` (as shown above) is still required because it remains part of the supported Router internals.

- **Memory:** Do not store `Path` objects; extract distance/time immediately and discard.

- **Matrix Consistency:**  
  `distances.length == sources.size()` and `distances[row].length == targets.size()` (same for `times`).

- **Error Handling:**
  - Snap failure → mark index in `failures` and set row/column = `-1`.
  - Unreachable route → cell = `-1`.
  - Profile missing → HTTP 400.
  - CH not available and `enableFallback == false` → HTTP 400.
  - Matrix too large → HTTP 400.

- **Concurrency:**
  - No shared mutable state across tasks.
  - Each task constructs its own `QueryGraph` and `RoutingAlgorithm`.
  - Writes only to its own matrix row.

---

## 8. Action

Generate the complete Java implementation for:

1. `MatrixRequest.java`
2. `MatrixResponse.java`
3. `MatrixBundle.java` (with shared `ExecutorService` & Dropwizard `Managed` integration)
4. `MatrixResource.java` (full CH/LM logic, snapping, parallel routing, metrics)
5. JUnit 5 tests (Unit, Integration, Concurrency)
6. JMH benchmark class (`MatrixBenchmark.java`)

Use only the modern GraphHopper 11.0 Core API and Dropwizard patterns as specified above.

---

## 9. Documentation — REQUIRED

You must generate all documentation files below.

### 9.1. docs/MATRIX_API_OVERVIEW.md

Explain:

- Purpose of custom matrix API
- Why OSS GraphHopper does not include Matrix API
- Architectural overview diagram (ASCII or PNG)
- Request/Response formats
- CH vs Flexible routing behavior
- Failure semantics (-1)

### 9.2. docs/THREAD_POOL_DESIGN.md

Cover:

- Why per-request thread pools are bad (thread exhaustion)
- Why a shared Managed ExecutorService is mandatory
- Thread-pool lifecycle (startup → shutdown)
- Diagram of request flow vs worker pool

### 9.3. docs/ERROR_HANDLING.md

Document:

- Snap failures
- Unreachable paths
- Profile not found
- CH missing + fallback disabled
- Matrix-size limits
- HTTP status codes & JSON response shapes

### 9.4. docs/PERFORMANCE_NOTES.md

Include:

- Benchmark methodology (JMH)
- Expected performance ranges (for CH)
- JVM tuning (-Xmx, GC settings)
- CH vs LM performance differences
- Parallelization strategy (1 task = 1 row)

### 9.5. docs/DEVELOPER_GUIDE.md

Explain:

- Code structure (DTOs, Resource, Bundle)
- How to add more metrics
- How to extend with future algorithms
- How to test with custom OSM datasets
- How to enable fallback routing safely
