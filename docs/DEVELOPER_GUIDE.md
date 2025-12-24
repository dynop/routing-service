# Developer Guide

## Code Structure
- `matrix-extension/src/main/java/com/dynop/graphhopper/matrix/api/MatrixRequest.java`
  - Immutable DTO, performs JSON validation and default expansion of `sources`/`targets`.
- `.../MatrixResponse.java`
  - Simple immutable response wrapper with `long[][]` payloads and a `failures` list.
- `.../MatrixResource.java`
  - Dropwizard JAX-RS resource that orchestrates validation, snapping, routing, metrics, and response construction.
- `.../config/MatrixBundle.java`
  - Adds the shared executor, metrics binding, and HK2 injections. Registers `MatrixResource` with Jersey.
- `.../config/MatrixGraphHopperProvider.java`
  - Bridge interface to reuse the already-started `GraphHopper` instance created by the core `GraphHopperBundle`.
- `MatrixServerApplication` / `MatrixServerConfiguration`
  - Bootstraps Dropwizard with both bundles and inherits all GraphHopper server settings.

## Adding More Metrics
1. Extend `MatrixRequest` validation to accept the new metric keyword.
2. Update `MatrixResource` to compute the value during `calcPath` (e.g., fuel burn, toll cost) and append another matrix to `MatrixResponse` or emit a parallel structure.
3. Document the new metric in `docs/MATRIX_API_OVERVIEW.md` and include regression tests.

## Extending with New Algorithms
- Plug additional routing strategies into `createFallbackFactory`. For example, if GraphHopper adds a multi-criteria factory, instantiate it when a profile hint such as `profile.getHints().get("strategy")` matches.
- Keep the per-row callable contract intact: each task must build its own `QueryGraph`/`RoutingAlgorithm` instance.
- Ensure the algorithm honors the `Weighting` produced via `graphHopper.createWeighting` so profile semantics stay consistent.

## Testing with Custom OSM Data
1. Import data via `./matrix-server import config.yml` (Dropwizard CLI command).
2. Point `graphhopper.config.yml` to the new `.osm.pbf` file and set `profiles`/`preparations` as needed.
3. Run `./matrix-server server config.yml` and POST to `/custom/matrix` using coordinates that reside inside the imported bounding box.
4. For repeatable regression tests, store the `.osm.pbf` snapshot under `osm-data/<region>` and reference it from your config.

## Enabling Fallback Routing Safely
- Set `enableFallback=true` in the request payload to allow LM or flexible routing when CH preparations are absent.
- Keep an eye on latency spikes; flexible routing can be orders of magnitude slower, so prefer enabling Landmark preparations for every production profile.
- Consider rate-limiting fallback requests if they are expected to be rare emergency operations.
