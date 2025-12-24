package com.dynop.graphhopper.matrix.sea;

/**
 * Represents the role of a port in a maritime route.
 * Used for error messages and logging to clarify which endpoint failed validation.
 */
public enum PortRole {
    /**
     * Port of Loading - the origin port where cargo is loaded onto the vessel.
     */
    PORT_OF_LOADING("POL"),
    
    /**
     * Port of Discharge - the destination port where cargo is unloaded from the vessel.
     */
    PORT_OF_DISCHARGE("POD");
    
    private final String abbreviation;
    
    PortRole(String abbreviation) {
        this.abbreviation = abbreviation;
    }
    
    /**
     * @return Short abbreviation for the port role (POL or POD)
     */
    public String getAbbreviation() {
        return abbreviation;
    }
}
