package com.dynop.graphhopper.matrix.config;

import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperBundleConfiguration;

/**
 * Bridge interface that exposes access to the already bootstrapped GraphHopper instance.
 * Implemented by the Dropwizard configuration so MatrixBundle can retrieve the shared GraphHopper.
 */
public interface MatrixGraphHopperProvider extends GraphHopperBundleConfiguration {

    /**
     * @return the running GraphHopper instance
     * @throws IllegalStateException if GraphHopper has not been started yet
     */
    GraphHopper requireGraphHopper();

    /**
     * Allows the Dropwizard application to store the shared GraphHopper after GraphHopperBundle starts.
     * A no-op default gives flexibility to legacy configurations.
     */
    default void setGraphHopper(GraphHopper graphHopper) {
        throw new UnsupportedOperationException("setGraphHopper not implemented");
    }
}
