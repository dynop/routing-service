package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.shapes.GHPoint;

import java.util.Objects;

/**
 * Snaps port coordinates to the nearest sea-lane graph node/edge.
 * 
 * <p>This is Stage 2 of the two-stage port snapping process:
 * <ol>
 *   <li><b>Stage 1:</b> User coordinate → UN/LOCODE seaport (handled by UnlocodePortSnapper)</li>
 *   <li><b>Stage 2 (this class):</b> Port coordinate → Sea-lane graph node</li>
 * </ol>
 * 
 * <p>Uses GraphHopper's LocationIndex for efficient spatial queries against the sea graph.
 * 
 * @see UnlocodePortSnapper
 */
public final class SeaNodeSnapper {
    
    /**
     * Default maximum snap distance from port to graph (300 km).
     */
    public static final double DEFAULT_MAX_SNAP_DISTANCE_METERS = 300_000;
    
    private final LocationIndex locationIndex;
    private final double maxSnapDistanceMeters;
    
    /**
     * Creates a sea node snapper with the given location index.
     * 
     * @param locationIndex GraphHopper's spatial index for the sea graph
     */
    public SeaNodeSnapper(LocationIndex locationIndex) {
        this(locationIndex, DEFAULT_MAX_SNAP_DISTANCE_METERS);
    }
    
    /**
     * Creates a sea node snapper with the given location index and max distance.
     * 
     * @param locationIndex         GraphHopper's spatial index for the sea graph
     * @param maxSnapDistanceMeters Maximum snap distance in meters
     */
    public SeaNodeSnapper(LocationIndex locationIndex, double maxSnapDistanceMeters) {
        this.locationIndex = Objects.requireNonNull(locationIndex, "locationIndex");
        this.maxSnapDistanceMeters = maxSnapDistanceMeters;
    }
    
    /**
     * Snap port coordinates to nearest sea-lane graph edge.
     * 
     * @param lat Port latitude
     * @param lon Port longitude
     * @return Snap result from GraphHopper's location index
     * @throws SeaNodeSnapException if port is too far from the sea-lane network
     */
    public Snap snapToGraph(double lat, double lon) {
        Snap snap = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        
        if (!snap.isValid()) {
            throw new SeaNodeSnapException(
                "GRAPH_SNAP_FAILED",
                lat, lon,
                "No valid snap point found"
            );
        }
        
        if (snap.getQueryDistance() > maxSnapDistanceMeters) {
            throw new SeaNodeSnapException(
                "GRAPH_SNAP_FAILED",
                lat, lon,
                String.format("Port %.1f meters from sea-lane network, exceeds maximum %.0f meters",
                    snap.getQueryDistance(), maxSnapDistanceMeters)
            );
        }
        
        return snap;
    }
    
    /**
     * Snap a GHPoint to the sea graph.
     * 
     * @param point Point to snap
     * @return Snap result
     */
    public Snap snapToGraph(GHPoint point) {
        return snapToGraph(point.getLat(), point.getLon());
    }
    
    /**
     * @return Maximum snap distance in meters
     */
    public double getMaxSnapDistanceMeters() {
        return maxSnapDistanceMeters;
    }
    
    /**
     * Exception thrown when sea node snapping fails.
     */
    public static class SeaNodeSnapException extends RuntimeException {
        
        private final String errorCode;
        private final double lat;
        private final double lon;
        
        public SeaNodeSnapException(String errorCode, double lat, double lon, String message) {
            super(String.format("%s: %s at (%.4f, %.4f)", errorCode, message, lat, lon));
            this.errorCode = errorCode;
            this.lat = lat;
            this.lon = lon;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
        
        public double getLat() {
            return lat;
        }
        
        public double getLon() {
            return lon;
        }
    }
}
