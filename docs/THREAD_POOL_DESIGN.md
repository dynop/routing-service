# Thread Pool Design

## Why Per-Request Executors Fail
Creating an `ExecutorService` inside every HTTP call would:
- Spawn thousands of short-lived threads, quickly exhausting OS limits and wasting CPU on context switches.
- Prevent Dropwizard from shutting down cleanly; abrupt termination can leave routing jobs half-finished.
- Hide back-pressure signals—callers keep receiving 200 OK even when the host is overloaded.

## Shared Managed ExecutorService
- `MatrixBundle` provisions a fixed-size pool (`Executors.newFixedThreadPool(...)`) during application startup.
- Pool size defaults to `Runtime.getRuntime().availableProcessors()` but can be overridden via `graphhopper.config.yml` (`matrix.executor.pool_size`).
- Threads are daemonized and named `matrix-worker-N` for observability.

### Lifecycle Management
1. **Bootstrap**
   - Bundle creates the pool and registers it with Dropwizard's lifecycle (`environment.lifecycle().manage(...)`).
   - Metrics registry and the executor are both bound to HK2 so `MatrixResource` receives them via constructor injection.
2. **Runtime**
   - Each matrix request submits one task per source row via `ExecutorService.invokeAll(...)`.
   - Tasks run to completion even if the HTTP client disconnects; cancellation can be added later because futures are tracked.
3. **Shutdown**
   - `ManagedExecutor.stop()` triggers `shutdown()` and waits up to 30 seconds before calling `shutdownNow()`.
   - Ensures running routes complete gracefully, preserving graph consistency.

## Request Flow vs Worker Pool
```
        +---------------------- Dropwizard Request Lifecycle ----------------------+
        |                                                                          |
Client ─┼──> Jersey / MatrixResource ── creates row callables ──┬──────────────────┘
        |                                                       v
        |                                               +---------------+
        |                                               | Executor Pool |
        |                                               | matrix-worker |
        |                                               +-------+-------+
        |                                                       |
        |                                                       v
        |                                               GraphHopper Core
        |                                                       |
        +───────────────────────────────────────────────────────┘
```
- Back-pressure is naturally applied: when all threads are busy, subsequent `invokeAll` submissions queue instead of spawning more threads.
- Metrics (Timer + Meter) provide visibility into queueing and throughput so pool sizes can be tuned.
