package com.dynop.graphhopper.matrix.config;

import com.graphhopper.GraphHopper;
import org.jetbrains.annotations.Nullable;

/**
 * Holder for the optional sea routing GraphHopper instance.
 * This wrapper allows HK2 to inject a non-null holder even when sea routing is disabled.
 */
public final class SeaHopperHolder {
    
    @Nullable
    private final GraphHopper seaHopper;
    
    public SeaHopperHolder(@Nullable GraphHopper seaHopper) {
        this.seaHopper = seaHopper;
    }
    
    /**
     * @return the sea GraphHopper instance, or null if sea routing is not configured
     */
    @Nullable
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
