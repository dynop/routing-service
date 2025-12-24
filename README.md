# Dynop Routing Service

A comprehensive routing service built on GraphHopper with matrix calculation capabilities and Pelias geocoding integration.

## Features

- **Matrix API**: Calculate distance and time matrices for multiple origin-destination pairs
- **GraphHopper Integration**: Optimized routing for truck profiles with custom configurations
- **Pelias Geocoding**: Address search and reverse geocoding capabilities
- **Ocean Routing**: Maritime navigation support with natural earth data

## Project Structure

- `matrix-extension/`: Java-based matrix calculation service
- `devtools/`: Development tools including GraphHopper and Pelias configurations
- `docs/`: Comprehensive documentation
- `backup/`: Docker configurations and deployment instructions

## Quick Start

See the detailed documentation in the `docs/` directory:
- [Developer Guide](docs/DEVELOPER_GUIDE.md)
- [Matrix API Overview](docs/MATRIX_API_OVERVIEW.md)
- [Performance Notes](docs/PERFORMANCE_NOTES.md)

## Data Files

Large data files (OSM files, graph caches) are excluded from the repository and should be downloaded separately.

## Development

Built with:
- Java/Maven
- GraphHopper
- Dropwizard
- Docker

## License

Copyright © 2025 Dynop
