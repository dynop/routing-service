# Performance Notes

## Benchmark Methodology (JMH)
- The `MatrixBenchmark` harness (under `matrix-extension/src/jmh/java`) runs warmups and measurements for three scenarios:
  1. **Small**: 10×10 matrix (100 routes) – validates overhead.
  2. **Medium**: 50×50 matrix (2,500 routes) – mirrors production dashboard refreshes.
  3. **Large**: 100×100 or 1000×1000 (10,000 to 1,000,000 routes) – stress test upper bounds.
- Each benchmark seeds deterministic coordinate grids so results are comparable across runs.
- Throughput metric (`Mode.Throughput`) reports routes/second; latency metric uses `Mode.AverageTime` per matrix.

## Expected Performance Bands (CH)
| Scenario | Target Throughput | Notes |
| --- | --- | --- |
| Small (10×10) | > 50,000 routes/sec | Dominated by HTTP/JSON overhead in real usage |
| Medium (50×50) | > 10,000 routes/sec | Baseline requirement from spec |
| Large (100×100) | ~5,000 routes/sec | Still CH-backed; memory footprint remains manageable |
| Large (1000×1000) | > 1,000 routes/sec | Requires ample heap (2–4 GB) and fast disks for graph data |

## JVM & Host Tuning
- Recommended flags: `-Xms4g -Xmx4g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:MaxGCPauseMillis=100`.
- Pin the process to NUMA nodes when running on multi-socket servers to reduce cross-node memory hops.
- Keep the shared executor pool size close to physical cores; oversubscription hurts cache locality for CH queries.

## CH vs LM/Flexible Performance
| Mode | Characteristics | Guidance |
| --- | --- | --- |
| CH | Fastest, precomputed shortcuts, deterministic memory usage | Enable for every profile used by matrix clients. |
| LM | Slightly slower than CH but still preprocessed; good fallback when CH shortcuts unavailable (e.g., turn-cost profiles). | Keep `graphHopper.router.default_active_landmarks` tuned (8–16). |
| Flexible (Dijkstra-Bi) | No preprocessing, highest latency; only used when both CH and LM are missing and fallback is enabled. | Restrict to small batches or administrative requests. |

## Parallelization Strategy
- One callable per source row keeps memory usage linear in row count and minimizes lock contention.
- Workers only mutate their respective `distances[rowIdx]`/`times[rowIdx]` arrays, eliminating synchronization.
- `ExecutorService.invokeAll` applies natural back-pressure: if all workers are busy, additional callables queue instead of spawning more threads, preventing route-starvation of the host.
