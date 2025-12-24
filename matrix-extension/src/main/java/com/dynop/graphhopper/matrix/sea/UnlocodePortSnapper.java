package com.dynop.graphhopper.matrix.sea;

import java.util.List;
import java.util.Objects;

/**
 * Snaps user coordinates to the nearest valid UN/LOCODE seaport.
 * 
 * <p>This is Stage 1 of the two-stage port snapping process:
 * <ol>
 *   <li><b>Stage 1 (this class):</b> User coordinate → UN/LOCODE seaport (POL/POD)</li>
 *   <li><b>Stage 2:</b> Port coordinate → Sea-lane graph node (handled by GraphHopper)</li>
 * </ol>
 * 
 * <p>Uses Haversine (great-circle) distance for nearest-neighbor search.
 * The same snapping logic is applied for both Port of Loading (POL) and 
 * Port of Discharge (POD).
 * 
 * @see PortSnapResult
 * @see PortSnapException
 */
public final class UnlocodePortSnapper {
    
    /**
     * Maximum distance in kilometers to snap user coordinates to a port.
     * Coordinates further than this from any seaport will be rejected.
     */
    public static final double DEFAULT_MAX_SNAP_DISTANCE_KM = 300.0;
    
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    private final List<Port> ports;
    private final double maxSnapDistanceKm;
    
    /**
     * Creates a port snapper with the given port list and default max distance.
     * 
     * @param ports List of ports to snap to
     */
    public UnlocodePortSnapper(List<Port> ports) {
        this(ports, DEFAULT_MAX_SNAP_DISTANCE_KM);
    }
    
    /**
     * Creates a port snapper with the given port list and max snap distance.
     * 
     * @param ports            List of ports to snap to
     * @param maxSnapDistanceKm Maximum snap distance in kilometers
     */
    public UnlocodePortSnapper(List<Port> ports, double maxSnapDistanceKm) {
        this.ports = Objects.requireNonNull(ports, "ports");
        this.maxSnapDistanceKm = maxSnapDistanceKm;
    }
    
    /**
     * Snap user coordinate to nearest valid UN/LOCODE seaport.
     * 
     * <p>This method is used for BOTH:
     * <ul>
     *   <li>Port of Loading (POL) — origin side</li>
     *   <li>Port of Discharge (POD) — destination side</li>
     * </ul>
     * The snapping rules are IDENTICAL for both endpoints.
     * 
     * @param lat  User latitude
     * @param lon  User longitude
     * @param role Port role (POL or POD) for error messages
     * @return PortSnapResult with snapped port details
     * @throws PortSnapException if no port found within range
     */
    public PortSnapResult snapToPort(double lat, double lon, PortRole role) {
        if (ports.isEmpty()) {
            throw new PortSnapException("NO_SEAPORT_FOUND", lat, lon, role);
        }
        
        Port nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Port port : ports) {
            double distance = haversineDistanceKm(lat, lon, port.getLat(), port.getLon());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = port;
            }
        }
        
        if (nearest == null) {
            throw new PortSnapException("NO_SEAPORT_FOUND", lat, lon, role);
        }
        
        // Enforce snap distance guardrail
        if (nearestDistance > maxSnapDistanceKm) {
            throw new PortSnapException(
                "NO_SEAPORT_WITHIN_RANGE",
                lat, lon,
                nearest.getUnlocode(),
                nearestDistance,
                role
            );
        }
        
        return new PortSnapResult(
            nearest.getUnlocode(),
            nearest.getName(),
            nearest.getLat(),
            nearest.getLon(),
            lat,
            lon,
            nearestDistance,
            "NEAREST_SEAPORT",
            role
        );
    }
    
    /**
     * Alias for {@link #snapToPort(double, double, PortRole)}.
     * 
     * @param lat  User latitude
     * @param lon  User longitude
     * @param role Port role (POL or POD)
     * @return PortSnapResult with snapped port details
     * @throws PortSnapException if no port found within range
     */
    public PortSnapResult snap(double lat, double lon, PortRole role) throws PortSnapException {
        return snapToPort(lat, lon, role);
    }
    
    /**
     * Calculate Haversine (great-circle) distance between two points.
     * 
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in kilometers
     */
    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * @return Maximum snap distance in kilometers
     */
    public double getMaxSnapDistanceKm() {
        return maxSnapDistanceKm;
    }
    
    /**
     * @return Number of ports in the snapper
     */
    public int getPortCount() {
        return ports.size();
    }
}
