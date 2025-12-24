#!/usr/bin/env python3
"""Integration validation for the dynop Pelias stack."""
from __future__ import annotations

import json
import os
import shlex
import math
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = PROJECT_ROOT / "config" / "pelias.json"
PELIAS_URL = os.environ.get("PELIAS_URL", "http://localhost:4000")
COMPOSE_CMD = shlex.split(os.environ.get("DOCKER_COMPOSE", "docker compose"))


def info(message: str) -> None:
    print(f"[integration] {message}")


def fail(message: str) -> None:
    print(f"[integration] ERROR: {message}", file=sys.stderr)
    sys.exit(1)


def run_compose(args: list[str]) -> subprocess.CompletedProcess:
    cmd = COMPOSE_CMD + args
    info(f"Running: {' '.join(cmd)}")
    return subprocess.run(cmd, cwd=PROJECT_ROOT, check=True, capture_output=True, text=True)


def validate_config() -> None:
    info(f"Validating Pelias config at {CONFIG_PATH}")
    if not CONFIG_PATH.exists():
        fail("pelias.json is missing")
    with CONFIG_PATH.open() as handle:
        config = json.load(handle)
    required_top = {"api", "imports", "elasticsearch", "interpolation", "placeholder"}
    if not required_top.issubset(config.keys()):
        fail(f"pelias.json missing keys: {required_top - set(config.keys())}")
    imports = config["imports"]
    if imports["osm"].get("filename") != "/osm-data/europe/europe-latest.osm.pbf":
        fail("pelias.json must reference the dynop OSM dataset path")
    if imports["csv"].get("filename") != "/data/custom.csv":
        fail("CSV filename must be /data/custom.csv")
    info("pelias.json structure OK")


def ensure_osm_file_present() -> None:
    info("Checking that the OSM PBF is visible inside pelias-osm")
    run_compose(["run", "--rm", "--profile", "import", "--no-deps", "-T", "pelias-osm", "ls", "/osm-data/europe/europe-latest.osm.pbf"])


def http_get(endpoint: str) -> dict:
    url = f"{PELIAS_URL}{endpoint}"
    info(f"HTTP GET {url}")
    with urllib.request.urlopen(url, timeout=30) as response:
        payload = response.read()
    return json.loads(payload)


def verify_libpostal_parsing() -> None:
    body = http_get("/v1/search?text=Friedrichstrasse+Berlin")
    parsed = body.get("geocoding", {}).get("query", {}).get("parsed_text", {})
    if "street" not in parsed:
        fail("Libpostal parsing did not populate parsed_text.street")
    info("Libpostal parsing validated")


def measure_p95_latency() -> None:
    latencies = []
    for _ in range(10):
        start = time.perf_counter()
        http_get("/v1/search?text=Berlin")
        latencies.append((time.perf_counter() - start) * 1000)
    latencies.sort()
    index = max(0, math.ceil(0.95 * len(latencies)) - 1)
    p95 = round(latencies[index], 2)
    info(f"p95 latency: {p95} ms")


def confirm_es_doc_count() -> None:
    result = run_compose(["exec", "-T", "elasticsearch", "curl", "-s", "http://localhost:9200/pelias/_count"])
    data = json.loads(result.stdout)
    count = data.get("count", 0)
    if count <= 10000:
        fail(f"Expected > 10000 documents, found {count}")
    info(f"Elasticsearch doc count OK ({count})")


def confirm_custom_csv_entries() -> None:
    body = http_get("/v1/search?text=Dynop+Distribution+Center+Berlin")
    names = [feat.get("properties", {}).get("name") for feat in body.get("features", [])]
    if not any(name and "Dynop Distribution Center" in name for name in names):
        fail("Custom CSV entry not found in Pelias results")
    info("Custom CSV entries available via API")


def main() -> None:
    try:
        validate_config()
        ensure_osm_file_present()
        verify_libpostal_parsing()
        measure_p95_latency()
        confirm_es_doc_count()
        confirm_custom_csv_entries()
        info("Integration tests completed successfully")
    except subprocess.CalledProcessError as exc:
        fail(exc.stderr.strip() or str(exc))
    except Exception as exc:  # pragma: no cover
        fail(str(exc))


if __name__ == "__main__":
    main()
