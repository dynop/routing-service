# Architecture

The dynop Pelias deployment sits entirely inside Docker Compose and uses a single bridged network `pelias-net`. The request flow is linear and deterministic:

1. **Client** issues REST queries to `pelias-api` (port 4000).
2. **Pelias API** validates input, fans out to supporting services, and orchestrates Elasticsearch lookups.
3. **Libpostal Service** normalizes and parses free-form text into structured components consumed by the API.
4. **Elasticsearch** stores all imported documents and returns ranked candidates for each query.
5. **PIP Service** converts raw coordinates into administrative hierarchies for reverse geocoding.
6. **Placeholder** enriches administrative names and ancestors to guarantee consistent place labels.
7. **Interpolation** fills in address ranges so partially-known addresses resolve cleanly.
8. **Response** travels back to the client with merged data plus metadata (timings, attribution, parsed text).

Supporting services such as MinIO and the importers run on the same network but stay isolated via Docker profiles so production traffic is unaffected during data refreshes.
