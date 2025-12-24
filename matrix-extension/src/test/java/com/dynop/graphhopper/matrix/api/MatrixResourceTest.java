package com.dynop.graphhopper.matrix.api;

import com.codahale.metrics.MetricRegistry;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatrixResourceTest {

    private static final String PROFILE = "truck";
    private static final List<String> METRICS = List.of("distance", "time");

    private GraphHopper graphHopper;
    private ExecutorService executorService;
    private LocationIndex locationIndex;
    private MatrixResource resource;

    @BeforeEach
    void setUp() throws Exception {
        graphHopper = mock(GraphHopper.class);
        executorService = mock(ExecutorService.class);
        locationIndex = mock(LocationIndex.class);

        Profile profile = new Profile(PROFILE).setWeighting("fastest");
        Weighting weighting = mock(Weighting.class);
        Map<String, com.graphhopper.storage.RoutingCHGraph> chGraphs = new HashMap<>();
        Map<String, com.graphhopper.routing.lm.LandmarkStorage> lmStores = new HashMap<>();

        when(graphHopper.getProfile(PROFILE)).thenReturn(profile);
        when(graphHopper.getLocationIndex()).thenReturn(locationIndex);
        when(graphHopper.getRouterConfig()).thenReturn(new com.graphhopper.routing.RouterConfig());
        when(graphHopper.createWeighting(eq(profile), any(PMap.class))).thenReturn(weighting);
        when(graphHopper.getCHGraphs()).thenReturn(chGraphs);
        when(graphHopper.getLandmarks()).thenReturn(lmStores);
        when(graphHopper.getBaseGraph()).thenReturn(mock(BaseGraph.class));

        when(executorService.invokeAll(ArgumentMatchers.<Collection<Callable<Void>>>any()))
                .thenAnswer(invocation -> {
                    Collection<Callable<Void>> tasks = invocation.getArgument(0);
                    List<Future<Void>> futures = new ArrayList<>(tasks.size());
                    for (int i = 0; i < tasks.size(); i++) {
                        futures.add(CompletableFuture.completedFuture(null));
                    }
                    return futures;
                });

        resource = new MatrixResource(graphHopper, executorService, new MetricRegistry(), 
                null, null, null);
    }

    @Test
    void chDisabledWithoutFallbackReturnsBadRequest() {
        MatrixRequest request = new MatrixRequest(
                List.of(List.of(0d, 0d), List.of(1d, 1d)),
                null,
                null,
                PROFILE,
                METRICS,
                false);

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> resource.compute(request));
        Response error = ex.getResponse();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), error.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) error.getEntity();
        assertEquals("CH not available for profile and fallback disabled", payload.get("message"));
    }

    @Test
    void snapFailuresFillEntireRowAndColumn() {
        List<List<Double>> points = List.of(List.of(0d, 0d), List.of(1d, 1d));
        when(locationIndex.findClosest(eq(0d), eq(0d), any()))
                .thenReturn(validSnap(0));
        when(locationIndex.findClosest(eq(1d), eq(1d), any()))
                .thenReturn(invalidSnap(1d, 1d));

        MatrixRequest request = new MatrixRequest(points, List.of(0, 1), List.of(0, 1), PROFILE, METRICS, true);

        Response response = resource.compute(request);
        MatrixResponse body = (MatrixResponse) response.getEntity();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(List.of(1), body.getFailures());
        assertEquals(-1, body.getDistances()[1][0]);
        assertEquals(-1, body.getDistances()[0][1]);
        assertEquals(-1, body.getTimes()[1][0]);
        assertEquals(-1, body.getTimes()[0][1]);
    }

    @Test
    void matrixDimensionsMatchSourcesAndTargets() {
        List<List<Double>> points = List.of(List.of(0d, 0d), List.of(2d, 2d), List.of(3d, 3d));
        when(locationIndex.findClosest(eq(0d), eq(0d), any()))
                .thenReturn(validSnap(0));
        when(locationIndex.findClosest(eq(2d), eq(2d), any()))
                .thenReturn(validSnap(1));
        when(locationIndex.findClosest(eq(3d), eq(3d), any()))
                .thenReturn(validSnap(2));

        MatrixRequest request = new MatrixRequest(points, List.of(0, 1), List.of(1, 2), PROFILE, METRICS, true);

        Response response = resource.compute(request);
        MatrixResponse body = (MatrixResponse) response.getEntity();

        assertEquals(2, body.getDistances().length);
        assertEquals(2, body.getDistances()[0].length);
        assertEquals(2, body.getTimes().length);
        assertEquals(2, body.getTimes()[0].length);
        assertTrue(body.getFailures().isEmpty());
    }

    private static Snap validSnap(int nodeId) {
        Snap snap = new Snap(0, 0);
        snap.setClosestNode(nodeId);
        return snap;
    }

    private static Snap invalidSnap(double lat, double lon) {
        return new Snap(lat, lon);
    }
}
