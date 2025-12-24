package com.dynop.graphhopper.matrix.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable request payload for the custom matrix endpoint.
 * 
 * <p>Supports both road and sea routing modes:
 * <ul>
 *   <li>{@code mode=road} (default): Uses the road graph for truck routing</li>
 *   <li>{@code mode=sea}: Uses the maritime graph for ocean freight routing</li>
 * </ul>
 * 
 * <p>For sea routing, additional parameters are available:
 * <ul>
 *   <li>{@code excluded_chokepoints}: List of chokepoint IDs to exclude (e.g., ["SUEZ"])</li>
 *   <li>{@code validate_coordinates}: Whether to validate coordinates against land mask</li>
 * </ul>
 */
public final class MatrixRequest {

    private static final Set<String> ALLOWED_METRICS = Set.of("distance", "time");

    private final List<List<Double>> points;
    private final List<Integer> sources;
    private final List<Integer> targets;
    private final String profile;
    private final List<String> metrics;
    private final boolean enableFallback;
    private final RoutingMode mode;
    private final List<String> excludedChokepoints;
    private final boolean validateCoordinates;

    @JsonCreator
    public MatrixRequest(
            @JsonProperty(value = "points", required = true) List<List<Double>> points,
            @JsonProperty("sources") List<Integer> sources,
            @JsonProperty("targets") List<Integer> targets,
            @JsonProperty(value = "profile", required = true) String profile,
            @JsonProperty(value = "metrics", required = true) List<String> metrics,
            @JsonProperty(value = "enableFallback", defaultValue = "false") Boolean enableFallback,
            @JsonProperty(value = "mode", defaultValue = "road") String mode,
            @JsonProperty(value = "excluded_chokepoints") List<String> excludedChokepoints,
            @JsonProperty(value = "validate_coordinates", defaultValue = "true") Boolean validateCoordinates) {

        this.points = validatePoints(points);
        this.sources = normalizeIndices(sources, this.points.size());
        this.targets = normalizeIndices(targets, this.points.size());
        this.profile = Objects.requireNonNull(profile, "profile is required");
        this.metrics = validateMetrics(metrics);
        this.enableFallback = Boolean.TRUE.equals(enableFallback);
        this.mode = parseRoutingMode(mode);
        this.excludedChokepoints = excludedChokepoints != null 
            ? Collections.unmodifiableList(new ArrayList<>(excludedChokepoints))
            : Collections.emptyList();
        this.validateCoordinates = validateCoordinates == null || validateCoordinates;
    }
    
    /**
     * Backward-compatible constructor without sea routing parameters.
     */
    public MatrixRequest(
            List<List<Double>> points,
            List<Integer> sources,
            List<Integer> targets,
            String profile,
            List<String> metrics,
            Boolean enableFallback) {
        this(points, sources, targets, profile, metrics, enableFallback, "road", null, true);
    }

    public List<List<Double>> getPoints() {
        return points;
    }

    public List<Integer> getSources() {
        return sources;
    }

    public List<Integer> getTargets() {
        return targets;
    }

    public String getProfile() {
        return profile;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public boolean isEnableFallback() {
        return enableFallback;
    }
    
    /**
     * @return Routing mode (ROAD or SEA)
     */
    public RoutingMode getMode() {
        return mode;
    }
    
    /**
     * @return List of chokepoint IDs to exclude from routing (sea mode only)
     */
    public List<String> getExcludedChokepoints() {
        return excludedChokepoints;
    }
    
    /**
     * @return Whether to validate coordinates against land mask (sea mode only)
     */
    public boolean isValidateCoordinates() {
        return validateCoordinates;
    }
    
    /**
     * @return true if this is a sea routing request
     */
    public boolean isSeaRouting() {
        return mode == RoutingMode.SEA;
    }
    
    private static RoutingMode parseRoutingMode(String mode) {
        if (mode == null || mode.isBlank() || mode.equalsIgnoreCase("road")) {
            return RoutingMode.ROAD;
        }
        if (mode.equalsIgnoreCase("sea")) {
            return RoutingMode.SEA;
        }
        throw new IllegalArgumentException("Invalid routing mode: " + mode + ". Valid values: road, sea");
    }

    private static List<List<Double>> validatePoints(List<List<Double>> rawPoints) {
        if (rawPoints == null || rawPoints.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }

        List<List<Double>> safePoints = new ArrayList<>(rawPoints.size());
        for (List<Double> coord : rawPoints) {
            if (coord == null || coord.size() != 2) {
                throw new IllegalArgumentException("Each point must be a [lat, lon] pair");
            }
            double lat = requireFinite(coord.get(0), "lat");
            double lon = requireFinite(coord.get(1), "lon");
            safePoints.add(List.of(lat, lon));
        }
        return Collections.unmodifiableList(safePoints);
    }

    private static double requireFinite(Double value, String label) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException(label + " must be a finite double");
        }
        return value;
    }

    private static List<Integer> normalizeIndices(List<Integer> indices, int limit) {
        if (indices == null || indices.isEmpty()) {
            List<Integer> fallback = new ArrayList<>(limit);
            for (int i = 0; i < limit; i++) {
                fallback.add(i);
            }
            return Collections.unmodifiableList(fallback);
        }
        List<Integer> safe = new ArrayList<>(indices.size());
        for (Integer idx : indices) {
            if (idx == null || idx < 0 || idx >= limit) {
                throw new IllegalArgumentException("indices must reference points list");
            }
            safe.add(idx);
        }
        return Collections.unmodifiableList(safe);
    }

    private static List<String> validateMetrics(List<String> requestedMetrics) {
        if (requestedMetrics == null || requestedMetrics.isEmpty()) {
            throw new IllegalArgumentException("metrics must not be empty");
        }
        Set<String> deduplicated = new HashSet<>();
        for (String metric : requestedMetrics) {
            if (!ALLOWED_METRICS.contains(metric)) {
                throw new IllegalArgumentException("Unsupported metric: " + metric);
            }
            deduplicated.add(metric);
        }
        return List.copyOf(deduplicated);
    }
}
