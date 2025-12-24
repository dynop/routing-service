package com.dynop.graphhopper.matrix.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Immutable DTO describing the matrix response payload.
 */
public final class MatrixResponse {

    private final long[][] distances;
    private final long[][] times;
    private final List<Integer> failures;

    @JsonCreator
    public MatrixResponse(
            @JsonProperty("distances") long[][] distances,
            @JsonProperty("times") long[][] times,
            @JsonProperty("failures") List<Integer> failures) {
        this.distances = distances;
        this.times = times;
        this.failures = failures == null ? List.of() : Collections.unmodifiableList(failures);
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
}
