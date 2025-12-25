package com.dynop.graphhopper.matrix;

import com.dynop.graphhopper.matrix.config.MatrixGraphHopperProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.graphhopper.GraphHopper;
import com.graphhopper.application.GraphHopperServerConfiguration;

/**
 * Configuration type for {@link MatrixServerApplication}. Inherits all GraphHopper server settings
 * and implements {@link MatrixGraphHopperProvider} to provide access to the shared GraphHopper instance.
 */
public class MatrixServerConfiguration extends GraphHopperServerConfiguration implements MatrixGraphHopperProvider {
    
    // Not from config - set programmatically after GraphHopperBundle starts
    @JsonIgnore
    private volatile GraphHopper graphHopper;

    @Override
    @JsonIgnore
    public GraphHopper requireGraphHopper() {
        if (graphHopper == null) {
            throw new IllegalStateException("GraphHopper has not been initialized yet");
        }
        return graphHopper;
    }

    @Override
    @JsonIgnore
    public void setGraphHopper(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
    }
}
