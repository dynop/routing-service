package com.dynop.graphhopper.matrix.sea;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EdgeFilter that excludes edges connected to chokepoint nodes.
 * 
 * <p>This filter is applied at query time to implement scenario-based chokepoint exclusion
 * (e.g., routing with Suez Canal closed).
 * 
 * <h2>Behavior</h2>
 * <ul>
 *   <li>If a chokepoint is excluded, ALL nodes/edges associated with that chokepoint 
 *       are treated as non-traversable</li>
 *   <li>Exclusion is deterministic — same exclusions = same results</li>
 *   <li>Applied at query time — does NOT mutate the graph</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * ChokepointAwareEdgeFilter filter = new ChokepointAwareEdgeFilter(
 *     List.of("SUEZ", "PANAMA"),
 *     chokepointRegistry
 * );
 * 
 * // Use in routing algorithm
 * Snap snap = locationIndex.findClosest(lat, lon, filter);
 * }</pre>
 * 
 * <p><b>Note:</b> All land geometry checks are performed at build time or for validation only.
 * Runtime routing NEVER queries land geometry.
 * 
 * @see ChokepointRegistry
 * @see Chokepoint
 */
public final class ChokepointAwareEdgeFilter implements EdgeFilter {
    
    private static final Logger LOGGER = Logger.getLogger(ChokepointAwareEdgeFilter.class.getName());
    
    private final Set<Integer> excludedNodeIds;
    private final List<String> excludedChokepoints;
    
    /**
     * Creates an edge filter that excludes the specified chokepoints.
     * 
     * @param excludedChokepoints List of chokepoint IDs to exclude (e.g., ["SUEZ", "PANAMA"])
     * @param registry            ChokepointRegistry to resolve node IDs
     */
    public ChokepointAwareEdgeFilter(List<String> excludedChokepoints, ChokepointRegistry registry) {
        this.excludedChokepoints = excludedChokepoints != null 
            ? Collections.unmodifiableList(new ArrayList<>(excludedChokepoints))
            : Collections.emptyList();
        
        this.excludedNodeIds = registry.getExcludedNodeIds(excludedChokepoints);
        
        if (!this.excludedChokepoints.isEmpty()) {
            LOGGER.log(Level.FINE, () -> String.format(
                "ChokepointAwareEdgeFilter: excluding %d nodes from chokepoints %s",
                excludedNodeIds.size(), this.excludedChokepoints));
        }
    }
    
    /**
     * Creates an edge filter with pre-computed excluded node IDs.
     * 
     * @param excludedNodeIds     Set of node IDs to exclude
     * @param excludedChokepoints List of chokepoint IDs (for logging/metadata)
     */
    public ChokepointAwareEdgeFilter(Set<Integer> excludedNodeIds, List<String> excludedChokepoints) {
        this.excludedNodeIds = excludedNodeIds != null
            ? Collections.unmodifiableSet(new HashSet<>(excludedNodeIds))
            : Collections.emptySet();
        this.excludedChokepoints = excludedChokepoints != null
            ? Collections.unmodifiableList(new ArrayList<>(excludedChokepoints))
            : Collections.emptyList();
    }
    
    /**
     * Creates an edge filter with only excluded node IDs.
     * 
     * @param excludedNodeIds Set of node IDs to exclude
     */
    public ChokepointAwareEdgeFilter(Set<Integer> excludedNodeIds) {
        this(excludedNodeIds, Collections.emptyList());
    }
    
    /**
     * Creates an edge filter that accepts all edges (no exclusions).
     */
    public static ChokepointAwareEdgeFilter acceptAll() {
        return new ChokepointAwareEdgeFilter(Collections.emptySet(), Collections.emptyList());
    }
    
    /**
     * Accept an edge if neither endpoint is an excluded chokepoint node.
     * 
     * @param edge Edge to evaluate
     * @return true if edge should be considered, false if excluded
     */
    @Override
    public boolean accept(EdgeIteratorState edge) {
        if (excludedNodeIds.isEmpty()) {
            return true;
        }
        
        return !excludedNodeIds.contains(edge.getBaseNode()) 
            && !excludedNodeIds.contains(edge.getAdjNode());
    }
    
    /**
     * @return Set of excluded node IDs
     */
    public Set<Integer> getExcludedNodeIds() {
        return excludedNodeIds;
    }
    
    /**
     * @return List of excluded chokepoint IDs
     */
    public List<String> getExcludedChokepoints() {
        return excludedChokepoints;
    }
    
    /**
     * @return true if any chokepoints are excluded
     */
    public boolean hasExclusions() {
        return !excludedNodeIds.isEmpty();
    }
    
    /**
     * @return Number of excluded nodes
     */
    public int getExcludedNodeCount() {
        return excludedNodeIds.size();
    }
    
    @Override
    public String toString() {
        return String.format("ChokepointAwareEdgeFilter{excludedChokepoints=%s, excludedNodes=%d}",
            excludedChokepoints, excludedNodeIds.size());
    }
}
