package com.dynop.graphhopper.matrix.sea;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of snapping a user coordinate to a UN/LOCODE seaport.
 * 
 * <p>Contains:
 * <ul>
 *   <li>The resolved UN/LOCODE port identifier</li>
 *   <li>Port name for display</li>
 *   <li>Snapped coordinates (the port's actual location)</li>
 *   <li>Original user coordinates</li>
 *   <li>Snap distance in kilometers</li>
 *   <li>Snap method used</li>
 *   <li>Port role (POL or POD)</li>
 * </ul>
 * 
 * @see UnlocodePortSnapper
 */
public final class PortSnapResult {
    
    private final String unlocode;
    private final String name;
    private final double lat;
    private final double lon;
    private final double originalLat;
    private final double originalLon;
    private final double snapDistanceKm;
    private final String snapMethod;
    private final PortRole role;
    
    /**
     * Constructs a PortSnapResult.
     * 
     * @param unlocode        UN/LOCODE identifier (e.g., "NLRTM")
     * @param name            Port name
     * @param lat             Snapped latitude (port location)
     * @param lon             Snapped longitude (port location)
     * @param originalLat     Original user-provided latitude
     * @param originalLon     Original user-provided longitude
     * @param snapDistanceKm  Distance from original to snapped location in km
     * @param snapMethod      Method used for snapping (e.g., "NEAREST_SEAPORT")
     * @param role            Port role (POL or POD)
     */
    @JsonCreator
    public PortSnapResult(
            @JsonProperty("unlocode") String unlocode,
            @JsonProperty("name") String name,
            @JsonProperty("lat") double lat,
            @JsonProperty("lon") double lon,
            @JsonProperty("originalLat") double originalLat,
            @JsonProperty("originalLon") double originalLon,
            @JsonProperty("snapDistanceKm") double snapDistanceKm,
            @JsonProperty("snapMethod") String snapMethod,
            @JsonProperty("role") PortRole role) {
        this.unlocode = unlocode;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.originalLat = originalLat;
        this.originalLon = originalLon;
        this.snapDistanceKm = snapDistanceKm;
        this.snapMethod = snapMethod;
        this.role = role;
    }
    
    /**
     * @return UN/LOCODE identifier (e.g., "NLRTM", "CNSHA")
     */
    public String getUnlocode() {
        return unlocode;
    }
    
    /**
     * @return Port name
     */
    public String getName() {
        return name;
    }
    
    /**
     * @return Snapped latitude (the port's actual location)
     */
    public double getLat() {
        return lat;
    }
    
    /**
     * @return Snapped longitude (the port's actual location)
     */
    public double getLon() {
        return lon;
    }
    
    /**
     * @return Original user-provided latitude
     */
    public double getOriginalLat() {
        return originalLat;
    }
    
    /**
     * @return Original user-provided longitude
     */
    public double getOriginalLon() {
        return originalLon;
    }
    
    /**
     * @return Distance from original to snapped location in kilometers
     */
    public double getSnapDistanceKm() {
        return snapDistanceKm;
    }
    
    /**
     * @return Method used for snapping (e.g., "NEAREST_SEAPORT")
     */
    public String getSnapMethod() {
        return snapMethod;
    }
    
    /**
     * @return Port role (POL or POD)
     */
    public PortRole getRole() {
        return role;
    }
    
    @Override
    public String toString() {
        return String.format("PortSnapResult{unlocode='%s', name='%s', lat=%.4f, lon=%.4f, " +
                        "snapDistanceKm=%.2f, role=%s}",
                unlocode, name, lat, lon, snapDistanceKm, role);
    }
}
