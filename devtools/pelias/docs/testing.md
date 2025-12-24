# Testing Strategy

## Automated Tests
1. **Smoke test (`tests/smoke_test.sh`)**
   - Performs health checks against `/v1/health`.
   - Exercises `/v1/search` with canonical queries (`Berlin`, `Distribution Center`).
   - Exits non-zero on any HTTP or payload failure.

2. **Integration test (`tests/integration_test.py`)**
   - Validates `config/pelias.json` structure plus required datasource paths.
   - Confirms `/osm-data/europe/europe-latest.osm.pbf` is visible from a container.
   - Calls Pelias endpoints to ensure libpostal parsing, p95 latency, and Dynop CSV entries.
   - Verifies Elasticsearch document counts exceed 10,000 via `docker compose exec`.

Run both via:

```bash
make test
```

## Manual Elasticsearch Validation
When debugging ingest or ranking issues:

1. Ensure the stack is running (`make up`).
2. Exec into Elasticsearch:
   ```bash
   docker compose exec elasticsearch bash
   ```
3. Use `_cat/indices` to inspect shards and docs:
   ```bash
   curl -s 'http://localhost:9200/_cat/indices?v'
   ```
4. Query specific docs:
   ```bash
   curl -s 'http://localhost:9200/pelias/_search?q=name:Berlin&size=5' | jq '.hits.hits[]._source.name'
   ```

Always exit the container and monitor logs (`docker compose logs -f pelias-api`) if responses look suspicious.
