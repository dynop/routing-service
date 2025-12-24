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
 */
public final class MatrixRequest {

    private static final Set<String> ALLOWED_METRICS = Set.of("distance", "time");

    private final List<List<Double>> points;
    private final List<Integer> sources;
    private final List<Integer> targets;
    private final String profile;
    private final List<String> metrics;
    private final boolean enableFallback;

    @JsonCreator
    public MatrixRequest(
            @JsonProperty(value = "points", required = true) List<List<Double>> points,
            @JsonProperty("sources") List<Integer> sources,
            @JsonProperty("targets") List<Integer> targets,
            @JsonProperty(value = "profile", required = true) String profile,
            @JsonProperty(value = "metrics", required = true) List<String> metrics,
            @JsonProperty(value = "enableFallback", defaultValue = "false") Boolean enableFallback) {

        this.points = validatePoints(points);
        this.sources = normalizeIndices(sources, this.points.size());
        this.targets = normalizeIndices(targets, this.points.size());
        this.profile = Objects.requireNonNull(profile, "profile is required");
        this.metrics = validateMetrics(metrics);
        this.enableFallback = Boolean.TRUE.equals(enableFallback);
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
