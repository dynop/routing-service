# Sea Routing for Supply Chain Management

## Overview

The DynOp Routing Service now includes **global ocean routing** capabilities, enabling supply chain managers to optimize maritime freight routes alongside existing road routing. This document explains the business value and usage of sea routing features.

## Key Business Benefits

### 1. Global Maritime Coverage
- Routes between any two seaports worldwide
- Uses UN/LOCODE standard port identifiers (e.g., `NLRTM` for Rotterdam, `SGSIN` for Singapore)
- Covers all major shipping lanes and maritime corridors

### 2. Chokepoint Avoidance
- **Suez Canal**: Alternative via Cape of Good Hope
- **Panama Canal**: Alternative via Cape Horn or Suez
- **Strait of Malacca**: Alternative routes through Indonesian straits
- **Strait of Hormuz**: Critical for oil tanker routing
- **Bosphorus**: Black Sea access alternatives
- **Bab-el-Mandeb**: Red Sea access point

### 3. Smart Port Snapping
When you provide coordinates (e.g., from a warehouse or factory), the system automatically:
1. Finds the nearest major seaport within 300km
2. Uses official UN/LOCODE port coordinates
3. Returns which port was selected and snap distance

## API Usage

### Basic Sea Routing Request

```json
{
  "mode": "sea",
  "points": [
    [51.9167, 4.5],    // Rotterdam area
    [1.2833, 103.85],  // Singapore area
    [35.4433, 139.638] // Yokohama area
  ],
  "sources": [0],
  "targets": [1, 2],
  "profile": "ship"
}
```

### Response Includes Port Information

```json
{
  "mode": "sea",
  "distances": [[12500000, 18000000]],
  "times": [[1500000000, 2160000000]],
  "failures": [],
  "port_snaps": [
    {
      "unlocode": "NLRTM",
      "name": "Rotterdam",
      "role": "POL",
      "snap_distance_km": 0.5
    },
    {
      "unlocode": "SGSIN",
      "name": "Singapore", 
      "role": "POD",
      "snap_distance_km": 1.2
    }
  ]
}
```

### Avoiding Chokepoints

To route around specific chokepoints (e.g., during canal closures or congestion):

```json
{
  "mode": "sea",
  "points": [[51.9167, 4.5], [1.2833, 103.85]],
  "sources": [0],
  "targets": [1],
  "profile": "ship",
  "excluded_chokepoints": ["SUEZ"]
}
```

This routes from Rotterdam to Singapore via the Cape of Good Hope instead of the Suez Canal.

## Chokepoint Reference

| ID | Name | Impact When Avoided |
|----|------|---------------------|
| `SUEZ` | Suez Canal | Europe-Asia routes go via Cape of Good Hope |
| `PANAMA` | Panama Canal | Pacific-Atlantic routes go via Cape Horn |
| `MALACCA` | Strait of Malacca | Routes through Lombok or Sunda Straits |
| `GIBRALTAR` | Strait of Gibraltar | Mediterranean access limited |
| `BOSPHORUS` | Bosphorus Strait | Black Sea access blocked |
| `HORMUZ` | Strait of Hormuz | Persian Gulf access blocked |
| `BAB_EL_MANDEB` | Bab-el-Mandeb | Red Sea/Suez access blocked |
| `CAPE_GOOD_HOPE` | Cape of Good Hope | Africa circumnavigation blocked |

## Use Cases

### 1. Freight Rate Comparison
Compare routing distances via different corridors:
- Rotterdam → Shanghai via Suez
- Rotterdam → Shanghai via Cape (exclude Suez)

### 2. Disruption Planning
When a canal is blocked or congested:
- Pre-calculate alternative routes
- Estimate additional transit times
- Adjust supply chain schedules

### 3. Multi-Modal Integration
Combine sea routing with existing road routing:
- Road: Factory → Seaport (mode=road)
- Sea: Port A → Port B (mode=sea)
- Road: Seaport → Distribution Center (mode=road)

### 4. Emissions Estimation
Longer routes via capes increase:
- Transit time
- Fuel consumption
- Carbon emissions

Use distance/time data to estimate environmental impact.

## Data Sources

- **Ports**: UN/LOCODE 2024-2 (over 40,000 location codes)
- **Land Boundaries**: Natural Earth 50m resolution
- **Shipping Lanes**: 5° global waypoint grid with chokepoint densification

## Limitations

- **No weather routing**: Routes are geodesic, not weather-optimized
- **Fixed speed**: Uses 30 km/h average (adjust in custom model)
- **Polar regions**: Latitudes beyond ±80° are not covered
- **Inland waterways**: Only ocean routes, not rivers/canals

## Getting Started

1. Ensure the sea graph is built (see Developer Guide)
2. Use `mode=sea` in your API requests
3. Start with major port-to-port routes
4. Experiment with chokepoint exclusions

## Support

For questions about sea routing implementation, contact the DynOp engineering team.
