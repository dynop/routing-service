SHELL := /bin/bash
COMPOSE := docker compose
PROJECT_NAME := $(notdir $(CURDIR))

.PHONY: bootstrap download import-all import-csv build up down clean test

bootstrap:
	@mkdir -p data/whosonfirst data/openaddresses data/downloads config logs
	@[ -f .env ] || (cp .env.example .env && echo "Created .env from template")
	@echo "Bootstrap complete."

WF_DATA_URL := https://data.geocode.earth/whosonfirst/whosonfirst-data-latest.tar.bz2
OA_DATA_URL := https://data.geocode.earth/openaddresses/openaddresses-europe.tar.gz

download:
	@mkdir -p data/whosonfirst data/openaddresses data/downloads
	@echo "Downloading WhosOnFirst ..."
	@curl -L "${WF_DATA_URL}" -o data/downloads/whosonfirst-data-latest.tar.bz2
	@echo "Downloading OpenAddresses ..."
	@curl -L "${OA_DATA_URL}" -o data/downloads/openaddresses-europe.tar.gz
	@echo "Using existing OSM dataset from GEO/osm-data/"

import-all:
	${COMPOSE} --profile import run --rm pelias-whosonfirst
	${COMPOSE} --profile import run --rm pelias-openaddresses
	${COMPOSE} --profile import run --rm pelias-osm
	${COMPOSE} --profile import run --rm pelias-csv-importer

import-csv:
	${COMPOSE} --profile import run --rm pelias-csv-importer

build:
	${COMPOSE} build pelias-placeholder pelias-interpolation

up:
	${COMPOSE} up -d elasticsearch pelias-api pelias-libpostal pelias-pip-service pelias-placeholder pelias-interpolation

down:
	${COMPOSE} down

clean:
	-@${COMPOSE} exec -T elasticsearch curl -fsSL -XDELETE 'http://localhost:9200/pelias' >/dev/null
	${COMPOSE} down -v --remove-orphans
	-@docker volume rm ${PROJECT_NAME}_pelias_esdata ${PROJECT_NAME}_pelias_minio_data >/dev/null 2>&1

test:
	./tests/smoke_test.sh
	python3 tests/integration_test.py
