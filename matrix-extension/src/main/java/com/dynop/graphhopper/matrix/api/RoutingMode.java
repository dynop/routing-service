package com.dynop.graphhopper.matrix.api;

/**
 * Enum representing the routing mode.
 * 
 * <ul>
 *   <li>{@link #ROAD} - Uses the road graph (truck routing)</li>
 *   <li>{@link #SEA} - Uses the maritime graph (ocean freight routing)</li>
 * </ul>
 */
public enum RoutingMode {
    /**
     * Road routing mode using the terrestrial road graph.
     */
    ROAD,
    
    /**
     * Sea routing mode using the maritime sea-lane graph.
     */
    SEA
}
