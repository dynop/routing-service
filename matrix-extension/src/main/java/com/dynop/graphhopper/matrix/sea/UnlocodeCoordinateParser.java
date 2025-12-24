package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.util.shapes.GHPoint;

import java.util.Optional;

/**
 * Parser for UN/LOCODE coordinate format to decimal degrees.
 * 
 * <p>UN/LOCODE uses a specific coordinate format: {@code DDMMH DDDMMH} where:
 * <ul>
 *   <li>{@code DD} or {@code DDD} = degrees (2 digits for lat, 3 for lon)</li>
 *   <li>{@code MM} = minutes</li>
 *   <li>{@code H} = hemisphere ({@code N}/{@code S} for latitude, {@code E}/{@code W} for longitude)</li>
 * </ul>
 * 
 * <p>Examples:
 * <ul>
 *   <li>{@code "5155N 00430E"} → 51.9167°N, 4.5°E (Rotterdam)</li>
 *   <li>{@code "3114N 12129E"} → 31.2333°N, 121.4833°E (Shanghai)</li>
 *   <li>{@code "4042N 07400W"} → 40.7°N, 74.0°W (New York)</li>
 * </ul>
 */
public final class UnlocodeCoordinateParser {
    
    private UnlocodeCoordinateParser() {
        // Utility class
    }
    
    /**
     * Parse UN/LOCODE coordinate format to decimal degrees.
     * 
     * @param coordString The coordinate string from UN/LOCODE CSV (e.g., "5155N 00430E")
     * @return Optional containing GHPoint, empty if parsing fails or coordinates missing
     */
    public static Optional<GHPoint> parse(String coordString) {
        if (coordString == null || coordString.isBlank()) {
            return Optional.empty();
        }
        
        String[] parts = coordString.trim().split("\\s+");
        if (parts.length != 2) {
            return Optional.empty();
        }
        
        try {
            double lat = parseLatitude(parts[0]);   // e.g., "5155N"
            double lon = parseLongitude(parts[1]);  // e.g., "00430E"
            
            // Validate coordinate ranges
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                return Optional.empty();
            }
            
            return Optional.of(new GHPoint(lat, lon));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Parse latitude in UN/LOCODE format.
     * Format: DDMMH (e.g., "5155N", "3114S")
     * 
     * @param latStr Latitude string
     * @return Decimal degrees (negative for South)
     */
    private static double parseLatitude(String latStr) {
        if (latStr.length() < 5) {
            throw new NumberFormatException("Invalid latitude format: " + latStr);
        }
        
        int degrees = Integer.parseInt(latStr.substring(0, 2));
        int minutes = Integer.parseInt(latStr.substring(2, 4));
        char hemisphere = Character.toUpperCase(latStr.charAt(4));
        
        if (hemisphere != 'N' && hemisphere != 'S') {
            throw new NumberFormatException("Invalid latitude hemisphere: " + hemisphere);
        }
        
        double decimal = degrees + (minutes / 60.0);
        return hemisphere == 'S' ? -decimal : decimal;
    }
    
    /**
     * Parse longitude in UN/LOCODE format.
     * Format: DDDMMH (e.g., "00430E", "12129W")
     * 
     * @param lonStr Longitude string
     * @return Decimal degrees (negative for West)
     */
    private static double parseLongitude(String lonStr) {
        if (lonStr.length() < 6) {
            throw new NumberFormatException("Invalid longitude format: " + lonStr);
        }
        
        int degrees = Integer.parseInt(lonStr.substring(0, 3));
        int minutes = Integer.parseInt(lonStr.substring(3, 5));
        char hemisphere = Character.toUpperCase(lonStr.charAt(5));
        
        if (hemisphere != 'E' && hemisphere != 'W') {
            throw new NumberFormatException("Invalid longitude hemisphere: " + hemisphere);
        }
        
        double decimal = degrees + (minutes / 60.0);
        return hemisphere == 'W' ? -decimal : decimal;
    }
}
