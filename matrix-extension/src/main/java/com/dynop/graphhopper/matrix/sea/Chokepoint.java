package com.dynop.graphhopper.matrix.sea;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Domain object representing a maritime chokepoint.
 * 
 * <p>Chokepoints are critical narrow passages in ocean shipping lanes that can be
 * enabled/disabled at query time to simulate real-world scenarios (e.g., Suez Canal closure).
 * 
 * <p>Each chokepoint consists of:
 * <ul>
 *   <li>A stable identifier (e.g., "SUEZ", "PANAMA")</li>
 *   <li>Associated graph node IDs within the sea-lane graph</li>
 *   <li>Geographic coordinates for the chokepoint center</li>
 *   <li>Configuration for densification (radius and step size)</li>
 * </ul>
 * 
 * <p>Chokepoints are tagged during graph build time and can be excluded at query time
 * via the {@code excluded_chokepoints} parameter in matrix requests.
 * 
 * @see ChokepointRegistry
 * @see ChokepointAwareEdgeFilter
 */
public final class Chokepoint {
    
    private final String id;              // e.g., "SUEZ", "PANAMA", "CAPE_GOOD_HOPE"
    private final String name;            // Human-readable name
    private final String region;          // Optional grouping (e.g., "AFRICA", "AMERICAS")
    private final double lat;             // Center latitude
    private final double lon;             // Center longitude
    private final double radiusDegrees;   // Densification radius in degrees
    private final double stepDegrees;     // Densification step size in degrees
    private final Set<Integer> nodeIds;   // Graph nodes belonging to this chokepoint
    private boolean enabled;              // Default = true
    
    /**
     * Constructs a new Chokepoint with specified densification parameters.
     * 
     * @param id            Stable identifier (e.g., "SUEZ")
     * @param name          Human-readable name
     * @param region        Optional region grouping
     * @param lat           Center latitude
     * @param lon           Center longitude
     * @param radiusDegrees Densification radius in degrees
     * @param stepDegrees   Densification step size in degrees
     * @param nodeIds       Set of graph node IDs (mutable, will be copied)
     */
    public Chokepoint(String id, String name, String region,
                      double lat, double lon,
                      double radiusDegrees, double stepDegrees,
                      Set<Integer> nodeIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.region = region != null ? region : "";
        this.lat = lat;
        this.lon = lon;
        this.radiusDegrees = radiusDegrees;
        this.stepDegrees = stepDegrees;
        this.nodeIds = nodeIds != null ? Set.copyOf(nodeIds) : Collections.emptySet();
        this.enabled = true;
    }
    
    /**
     * Constructs a Chokepoint definition before graph nodes are assigned.
     * Use {@link #withNodeIds(Set)} to create a copy with node IDs after graph building.
     */
    public Chokepoint(String id, String name, String region,
                      double lat, double lon,
                      double radiusDegrees, double stepDegrees) {
        this(id, name, region, lat, lon, radiusDegrees, stepDegrees, null);
    }
    
    /**
     * @return Stable chokepoint identifier (e.g., "SUEZ", "PANAMA")
     */
    public String getId() {
        return id;
    }
    
    /**
     * @return Human-readable chokepoint name
     */
    public String getName() {
        return name;
    }
    
    /**
     * @return Optional region grouping (e.g., "AFRICA", "AMERICAS")
     */
    public String getRegion() {
        return region;
    }
    
    /**
     * @return Center latitude of the chokepoint
     */
    public double getLat() {
        return lat;
    }
    
    /**
     * @return Center longitude of the chokepoint
     */
    public double getLon() {
        return lon;
    }
    
    /**
     * @return Densification radius in degrees
     */
    public double getRadiusDegrees() {
        return radiusDegrees;
    }
    
    /**
     * @return Densification step size in degrees
     */
    public double getStepDegrees() {
        return stepDegrees;
    }
    
    /**
     * @return Immutable set of graph node IDs belonging to this chokepoint
     */
    public Set<Integer> getNodeIds() {
        return nodeIds;
    }
    
    /**
     * @return true if this chokepoint is enabled (traversable)
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Sets the enabled state of this chokepoint.
     * When disabled, routing algorithms will treat the chokepoint nodes as non-traversable.
     * 
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Creates a copy of this chokepoint with the specified node IDs.
     * Used after graph building to assign actual graph nodes to the chokepoint.
     * 
     * @param nodeIds Set of graph node IDs
     * @return New Chokepoint instance with assigned node IDs
     */
    public Chokepoint withNodeIds(Set<Integer> nodeIds) {
        return new Chokepoint(id, name, region, lat, lon, radiusDegrees, stepDegrees, nodeIds);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chokepoint that = (Chokepoint) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Chokepoint{id='%s', name='%s', lat=%.4f, lon=%.4f, nodeCount=%d, enabled=%s}",
                id, name, lat, lon, nodeIds.size(), enabled);
    }
}
