package com.dynop.graphhopper.matrix.api;

import com.dynop.graphhopper.matrix.sea.PortSnapResult;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Immutable DTO describing the matrix response payload.
 * 
 * <p>For sea routing requests, additional metadata is included:
 * <ul>
 *   <li>{@code port_snaps}: Port snapping results for each input point</li>
 *   <li>{@code excluded_chokepoints}: Chokepoints that were excluded from routing</li>
 *   <li>{@code mode}: The routing mode used (road or sea)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MatrixResponse {

    private final long[][] distances;
    private final long[][] times;
    private final List<Integer> failures;
    private final RoutingMode mode;
    private final List<String> excludedChokepoints;
    private final List<PortSnapResult> portSnaps;
    private final String error;
    private final String errorCode;

    /**
     * Full constructor for sea routing responses with all metadata.
     */
    @JsonCreator
    public MatrixResponse(
            @JsonProperty("distances") long[][] distances,
            @JsonProperty("times") long[][] times,
            @JsonProperty("failures") List<Integer> failures,
            @JsonProperty("mode") RoutingMode mode,
            @JsonProperty("excluded_chokepoints") List<String> excludedChokepoints,
            @JsonProperty("port_snaps") List<PortSnapResult> portSnaps,
            @JsonProperty("error") String error,
            @JsonProperty("errorCode") String errorCode) {
        this.distances = distances;
        this.times = times;
        this.failures = failures == null ? List.of() : Collections.unmodifiableList(failures);
        this.mode = mode;
        this.excludedChokepoints = excludedChokepoints == null ? null : Collections.unmodifiableList(excludedChokepoints);
        this.portSnaps = portSnaps == null ? null : Collections.unmodifiableList(portSnaps);
        this.error = error;
        this.errorCode = errorCode;
    }
    
    /**
     * Backward-compatible constructor for road routing responses.
     */
    public MatrixResponse(long[][] distances, long[][] times, List<Integer> failures) {
        this(distances, times, failures, RoutingMode.ROAD, null, null, null, null);
    }
    
    /**
     * Constructor for routing responses with mode.
     */
    public MatrixResponse(long[][] distances, long[][] times, List<Integer> failures, RoutingMode mode) {
        this(distances, times, failures, mode, null, null, null, null);
    }
    
    /**
     * Constructor for sea routing responses with port snapping and chokepoint metadata.
     */
    public MatrixResponse(long[][] distances, long[][] times, List<Integer> failures,
                          RoutingMode mode, List<String> excludedChokepoints, List<PortSnapResult> portSnaps) {
        this(distances, times, failures, mode, excludedChokepoints, portSnaps, null, null);
    }
    
    /**
     * Create an error response.
     */
    public static MatrixResponse failure(String errorCode, String message) {
        return new MatrixResponse(null, null, null, null, null, null, message, errorCode);
    }
    
    /**
     * Create an error response with a simple message.
     */
    public static MatrixResponse failure(String message) {
        return new MatrixResponse(null, null, null, null, null, null, message, "ERROR");
    }

    public long[][] getDistances() {
        return distances;
    }

    public long[][] getTimes() {
        return times;
    }

    public List<Integer> getFailures() {
        return failures;
    }
    
    /**
     * @return The routing mode used (ROAD or SEA)
     */
    @JsonProperty("mode")
    public RoutingMode getMode() {
        return mode;
    }
    
    /**
     * @return List of chokepoints that were excluded (sea mode only)
     */
    @JsonProperty("excluded_chokepoints")
    public List<String> getExcludedChokepoints() {
        return excludedChokepoints;
    }
    
    /**
     * @return Port snapping results for each input point (sea mode only)
     */
    @JsonProperty("port_snaps")
    public List<PortSnapResult> getPortSnaps() {
        return portSnaps;
    }
    
    /**
     * @return Error message if the request failed
     */
    public String getError() {
        return error;
    }
    
    /**
     * @return Error code if the request failed
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * @return true if this response represents an error
     */
    public boolean isError() {
        return error != null || errorCode != null;
    }
}
