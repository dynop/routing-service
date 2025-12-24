# Troubleshooting Guide

## Elasticsearch OOM
- Verify kernel map count is configured (`vm.max_map_count=262144`).
- Reduce heap size via `.env` (`ES_HEAP_SIZE_GB`, `ES_JAVA_OPTS`) if the host cannot spare 4 GB.
- Monitor memory with `docker stats pelias-elasticsearch` and inspect logs under `docker compose logs elasticsearch` for GC thrashing.

## Missing Interpolation Data
- Ensure `make download` completed successfully; interpolation relies on OpenAddresses archives.
- Rebuild the interpolation container caches:
  ```bash
  docker compose build pelias-interpolation
  docker compose run --rm --profile import pelias-openaddresses
  ```
- Confirm `/config/interpolation_sources.json` references real files.

## Missing Placeholder Data
- Run the Placeholder importer or refresh the dataset with `make import-all`.
- Double-check `/config/placeholder_sources.json` paths and the `data/whosonfirst` directory.
- Tail logs with `docker compose logs pelias-placeholder` to spot parse errors.

## vm.max_map_count
If Elasticsearch fails immediately with `bootstrap checks failed`, re-run:
```bash
sudo sysctl -w vm.max_map_count=262144
```
and persist it via `/etc/sysctl.d/99-elasticsearch.conf` as shown in the README.

## "No Results" Debugging Sequence
1. Confirm the API is healthy: `curl -f http://localhost:4000/v1/health`.
2. Check Elasticsearch docs: `docker compose exec -T elasticsearch curl -s 'http://localhost:9200/pelias/_count'`.
3. Inspect ingestion logs for the relevant importer via `docker compose logs pelias-osm`.
4. Re-import specific datasets (`make import-csv` or the targeted importer).
5. Validate supporting services (libpostal, pip, placeholder) with their health endpoints.
