package com.dynop.graphhopper.matrix.sea;

import java.util.Objects;

/**
 * Domain object representing a UN/LOCODE seaport.
 * 
 * <p>Ports are loaded from the official UN/LOCODE data files and used for:
 * <ul>
 *   <li>Port snapping - matching user coordinates to valid seaports</li>
 *   <li>POL/POD resolution - determining authoritative maritime endpoints</li>
 *   <li>Port metadata - providing names, locations, and function codes</li>
 * </ul>
 * 
 * @see UnlocodePortLoader
 */
public final class Port {
    
    private final String unlocode;      // e.g., "NLRTM", "CNSHA"
    private final String name;          // e.g., "Rotterdam", "Shanghai"
    private final String countryCode;   // e.g., "NL", "CN"
    private final String subdivision;   // e.g., "ZH" (Zuid-Holland)
    private final double lat;
    private final double lon;
    private final String function;      // e.g., "12345---"
    private final String status;        // e.g., "AF", "AI"
    
    /**
     * Constructs a new Port instance.
     * 
     * @param unlocode     UN/LOCODE identifier (e.g., "NLRTM")
     * @param name         Port name (ASCII, no diacritics)
     * @param countryCode  ISO 3166 alpha-2 country code
     * @param subdivision  ISO 3166-2 subdivision code (may be empty)
     * @param lat          Latitude in decimal degrees
     * @param lon          Longitude in decimal degrees
     * @param function     8-character function code string
     * @param status       Entry status code (e.g., "AI", "AF")
     */
    public Port(String unlocode, String name, String countryCode, String subdivision,
                double lat, double lon, String function, String status) {
        this.unlocode = Objects.requireNonNull(unlocode, "unlocode");
        this.name = Objects.requireNonNull(name, "name");
        this.countryCode = Objects.requireNonNull(countryCode, "countryCode");
        this.subdivision = subdivision != null ? subdivision : "";
        this.lat = lat;
        this.lon = lon;
        this.function = Objects.requireNonNull(function, "function");
        this.status = Objects.requireNonNull(status, "status");
    }
    
    /**
     * @return UN/LOCODE identifier (e.g., "NLRTM", "CNSHA")
     */
    public String getUnlocode() {
        return unlocode;
    }
    
    /**
     * @return Port name in ASCII (no diacritics)
     */
    public String getName() {
        return name;
    }
    
    /**
     * @return ISO 3166 alpha-2 country code
     */
    public String getCountryCode() {
        return countryCode;
    }
    
    /**
     * @return ISO 3166-2 subdivision code (may be empty)
     */
    public String getSubdivision() {
        return subdivision;
    }
    
    /**
     * @return Latitude in decimal degrees
     */
    public double getLat() {
        return lat;
    }
    
    /**
     * @return Longitude in decimal degrees
     */
    public double getLon() {
        return lon;
    }
    
    /**
     * @return 8-character UN/LOCODE function code string
     */
    public String getFunction() {
        return function;
    }
    
    /**
     * @return Entry status code (e.g., "AI", "AF", "RL")
     */
    public String getStatus() {
        return status;
    }
    
    /**
     * Check if this is a major port with multiple transport modes.
     * Major ports typically have port + rail + road connections.
     * 
     * @return true if the port has 3 or more transport functions
     */
    public boolean isMajorPort() {
        return function.chars().filter(c -> c != '-').count() >= 3;
    }
    
    /**
     * Check if port has rail connection (position 2 = '2').
     * 
     * @return true if the port has rail terminal capability
     */
    public boolean hasRailConnection() {
        return function.length() > 1 && function.charAt(1) == '2';
    }
    
    /**
     * Check if port has road connection (position 3 = '3').
     * 
     * @return true if the port has road terminal capability
     */
    public boolean hasRoadConnection() {
        return function.length() > 2 && function.charAt(2) == '3';
    }
    
    /**
     * Check if port has airport (position 4 = '4').
     * 
     * @return true if the port has airport capability
     */
    public boolean hasAirport() {
        return function.length() > 3 && function.charAt(3) == '4';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Port port = (Port) o;
        return unlocode.equals(port.unlocode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(unlocode);
    }
    
    @Override
    public String toString() {
        return String.format("Port{unlocode='%s', name='%s', lat=%.4f, lon=%.4f, function='%s'}",
                unlocode, name, lat, lon, function);
    }
}
