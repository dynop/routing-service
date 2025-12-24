package com.dynop.graphhopper.matrix.config;

import com.dynop.graphhopper.matrix.api.RoutingMode;
import com.graphhopper.GraphHopper;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Registry holding GraphHopper instances for different routing modes.
 * 
 * <p>This registry provides access to:
 * <ul>
 *   <li><b>Road hopper:</b> For truck routing using the road graph</li>
 *   <li><b>Sea hopper:</b> For maritime routing using the sea-lane graph</li>
 * </ul>
 * 
 * <p>Mode selection occurs at API level via {@code MatrixRequest.mode}.
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get the appropriate hopper for the request mode
 * GraphHopper hopper = registry.getHopper(request.getMode());
 * }</pre>
 * 
 * @see RoutingMode
 */
public final class RoutingEngineRegistry {
    
    private static final Logger LOGGER = Logger.getLogger(RoutingEngineRegistry.class.getName());
    
    private final GraphHopper roadHopper;
    private final GraphHopper seaHopper;
    
    /**
     * Creates a registry with both road and sea hoppers.
     * 
     * @param roadHopper GraphHopper instance for road routing
     * @param seaHopper  GraphHopper instance for sea routing (may be null if not configured)
     */
    public RoutingEngineRegistry(GraphHopper roadHopper, GraphHopper seaHopper) {
        this.roadHopper = Objects.requireNonNull(roadHopper, "roadHopper");
        this.seaHopper = seaHopper;
        
        if (seaHopper != null) {
            LOGGER.info("RoutingEngineRegistry initialized with road and sea hoppers");
        } else {
            LOGGER.info("RoutingEngineRegistry initialized with road hopper only");
        }
    }
    
    /**
     * Creates a registry with only the road hopper.
     * Sea routing will not be available.
     * 
     * @param roadHopper GraphHopper instance for road routing
     */
    public RoutingEngineRegistry(GraphHopper roadHopper) {
        this(roadHopper, null);
    }
    
    /**
     * Get the GraphHopper instance for the specified routing mode.
     * 
     * @param mode Routing mode (ROAD or SEA)
     * @return GraphHopper instance for the mode
     * @throws IllegalArgumentException if mode is SEA and sea hopper is not configured
     */
    public GraphHopper getHopper(RoutingMode mode) {
        return switch (mode) {
            case ROAD -> roadHopper;
            case SEA -> {
                if (seaHopper == null) {
                    throw new IllegalArgumentException(
                        "Sea routing is not configured. Ensure sea graph is built and loaded.");
                }
                yield seaHopper;
            }
        };
    }
    
    /**
     * @return The road hopper instance
     */
    public GraphHopper getRoadHopper() {
        return roadHopper;
    }
    
    /**
     * @return The sea hopper instance, or null if not configured
     */
    public GraphHopper getSeaHopper() {
        return seaHopper;
    }
    
    /**
     * @return true if sea routing is available
     */
    public boolean isSeaRoutingAvailable() {
        return seaHopper != null;
    }
}
