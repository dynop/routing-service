package com.dynop.graphhopper.matrix.sea;

/**
 * Exception thrown when port snapping fails.
 * 
 * <p>Possible error codes:
 * <ul>
 *   <li>{@code NO_SEAPORT_FOUND} - No seaports in the port database</li>
 *   <li>{@code NO_SEAPORT_WITHIN_RANGE} - Nearest seaport exceeds maximum snap distance</li>
 *   <li>{@code COORDINATE_ON_LAND} - Coordinate validation detected land position</li>
 *   <li>{@code POLAR_REGION_UNSUPPORTED} - Latitude outside ±80° range</li>
 * </ul>
 */
public class PortSnapException extends RuntimeException {
    
    private final String errorCode;
    private final double lat;
    private final double lon;
    private final PortRole role;
    private final String nearestUnlocode;
    private final double distanceKm;
    
    /**
     * Creates a port snap exception with minimal info.
     * 
     * @param errorCode Error code
     * @param lat       Latitude
     * @param lon       Longitude
     * @param role      Port role (POL or POD)
     */
    public PortSnapException(String errorCode, double lat, double lon, PortRole role) {
        super(String.format("%s: No seaport found for %s at (%.4f, %.4f)",
                errorCode, role.getAbbreviation(), lat, lon));
        this.errorCode = errorCode;
        this.lat = lat;
        this.lon = lon;
        this.role = role;
        this.nearestUnlocode = null;
        this.distanceKm = -1;
    }
    
    /**
     * Creates a port snap exception with nearest port info.
     * 
     * @param errorCode        Error code
     * @param lat              Latitude
     * @param lon              Longitude
     * @param nearestUnlocode  UN/LOCODE of nearest port
     * @param distanceKm       Distance to nearest port in km
     * @param role             Port role (POL or POD)
     */
    public PortSnapException(String errorCode, double lat, double lon,
                             String nearestUnlocode, double distanceKm, PortRole role) {
        super(String.format("%s: Nearest seaport %s is %.1f km away from %s at (%.4f, %.4f), " +
                        "exceeds maximum snap distance",
                errorCode, nearestUnlocode, distanceKm, role.getAbbreviation(), lat, lon));
        this.errorCode = errorCode;
        this.lat = lat;
        this.lon = lon;
        this.role = role;
        this.nearestUnlocode = nearestUnlocode;
        this.distanceKm = distanceKm;
    }
    
    /**
     * @return Error code (e.g., "NO_SEAPORT_WITHIN_RANGE")
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * @return Original latitude
     */
    public double getLat() {
        return lat;
    }
    
    /**
     * @return Original longitude
     */
    public double getLon() {
        return lon;
    }
    
    /**
     * @return Port role (POL or POD)
     */
    public PortRole getRole() {
        return role;
    }
    
    /**
     * @return UN/LOCODE of nearest port, or null if not available
     */
    public String getNearestUnlocode() {
        return nearestUnlocode;
    }
    
    /**
     * @return Distance to nearest port in km, or -1 if not available
     */
    public double getDistanceKm() {
        return distanceKm;
    }
}
