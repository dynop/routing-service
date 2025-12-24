# dynop Pelias Stack

Self-hosted Pelias environment aligned with the dynop GEO subsystem requirements.

## Kernel Requirement
Before starting Elasticsearch, configure the kernel virtual memory map limits:

```bash
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-elasticsearch.conf
```

## OSM Dataset Source
Pelias uses the existing dynop OSM dataset located under:

```
GEO/osm-data/<region>/<file>.osm.pbf
```

No additional downloads are performed for OSM—ensure the target region file exists before imports.

## Quickstart
```bash
cd GEO/devtools/pelias
make bootstrap
make download
make import-all
make build
make up
./tests/smoke_test.sh
```

The `bootstrap` target provisions folders and copies `.env.example` to `.env` if needed. `download` fetches WhosOnFirst and OpenAddresses datasets while reusing the existing OSM payload.

## MinIO Console Access
MinIO runs headless by default. To expose the console temporarily on port 9001:

```bash
docker compose port minio 9001
```

Run `make down` after maintenance to return to the secure default.
