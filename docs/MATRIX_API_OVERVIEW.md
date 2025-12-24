# Matrix API Overview

## Purpose
- Deliver a production-ready N×M routing matrix endpoint embedded directly in the existing GraphHopper Dropwizard server (`MatrixResource`), avoiding the need for an external commercial service.
- Reuse the already-initialized `GraphHopper` instance so matrix requests share graph data, profiles, and CH/LM preparations with the main server.
- Provide predictable failure semantics (negative values) so downstream logistics tooling can make deterministic fallback decisions.

## Why OSS GraphHopper Lacks This Capability
Open-source GraphHopper exposes one-to-one (routing) and one-to-many (isoline) APIs, but the high-performance Matrix API remains part of the commercial offering. The community edition omits:
- Dropwizard wiring for a matrix HTTP resource.
- Shared thread-pool orchestration for thousands of concurrent shortest-path jobs.
- Consistent JSON contracts for partial failure handling.
This repository supplies those missing layers while still relying on the sanctioned GraphHopper 11.0 Core APIs so upgrades remain compatible.

## Architectural Overview
```
+-------------+       HTTPS        +--------------------+      Managed Executor       +-----------------+
| Matrix      |  POST /custom/...  | MatrixResource     |  submits N row tasks  --->  | matrix-worker-* |
| Client(s)   +------------------->+  (Dropwizard JAX-RS)|                           | threads in pool |
+-------------+                    |  - validation       |<--- shares stats ---------+-----------------+
                                   |  - snapping         |        (Timer/Meter)
                                   |  - task creation    |
                                   +---------+----------+
                                             |
                                             v
                                      +-------------+
                                      | GraphHopper |
                                      | (CH/LM/Flex)|
                                      +-------------+
```
- One HTTP request allocates per-row tasks that reuse the shared executor created in `MatrixBundle` at startup.
- Each worker constructs its own `QueryGraph`/`RoutingAlgorithm` to stay thread-safe while reading the shared routing data structures.

## Request & Response Contracts
### Request JSON (`MatrixRequest`)
```json
{
  "points": [[lat, lon], ...],
  "sources": [0, 3, 5],          // optional; defaults to all indices
  "targets": [1, 2, 4],          // optional; defaults to all indices
  "profile": "truck",           // must exist in graphhopper.config.yml
  "metrics": ["distance", "time"],
  "enableFallback": true         // allow LM/Flexible routing if CH missing
}
```
Validation:
- `points` must contain at least one `[lat, lon]` pair of finite doubles.
- `metrics` must be a non-empty subset of `{distance, time}`.
- Index arrays must reference valid point positions; missing arrays expand to the full range.

### Response JSON (`MatrixResponse`)
```json
{
  "distances": [[12, -1, 45], ...], // meters; -1 => unreachable/un-snapped
  "times": [[90, -1, 180], ...],    // milliseconds; -1 mirrors distances
  "failures": [1, 8]                // point indices that could not snap to the graph
}
```
Rules:
- Rows correspond to `sources`, columns to `targets`.
- If an index appears in `failures`, the related row and/or column is pre-filled with `-1`.
- Individual cells become `-1` when the routing algorithm reports `!path.isFound()` or throws `ConnectionNotFoundException`.

## CH vs Flexible Routing Behavior
1. **CH Prepared Graph Available**
   - `MatrixResource` builds a `QueryRoutingCHGraph` and `CHRoutingAlgorithmFactory` per task.
   - Fastest option; default for production workloads.
2. **CH Missing, `enableFallback = true`**
   - If LM preparations exist, an `LMRoutingAlgorithmFactory` is used with the configured number of active landmarks.
   - Otherwise the system falls back to `RoutingAlgorithmFactorySimple` (flexible Dijkstra-Bi).
3. **CH Missing, `enableFallback = false`**
   - Request is rejected with HTTP 400 and JSON `{ "message": "CH not available for profile and fallback disabled" }`.

## Failure Semantics
- **Snap Failures**: Points that cannot be matched to the road graph populate `failures` and set every affected matrix entry to `-1`.
- **Unreachable Routes**: Either `Path.isFound()` returns false or routing throws `ConnectionNotFoundException`; the specific cell is set to `-1`.
- **Matrix Limits**: Matrices larger than 5000×5000 (25M cells) are rejected before any work begins, ensuring predictable memory usage.
