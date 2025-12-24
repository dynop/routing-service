package com.dynop.graphhopper.matrix.api;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.RoutingAlgorithmFactorySimple;
import com.graphhopper.routing.ch.CHRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LMRoutingAlgorithmFactory;
import com.graphhopper.routing.lm.LandmarkStorage;
import com.graphhopper.routing.querygraph.QueryGraph;
import com.graphhopper.routing.querygraph.QueryRoutingCHGraph;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RoutingCHGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.exceptions.ConnectionNotFoundException;
import com.graphhopper.util.shapes.GHPoint3D;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * REST resource that exposes the high-performance matrix endpoint.
 */
@Path("/custom/matrix")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MatrixResource {

    private static final int MAX_DIMENSION = 5_000;

    private final GraphHopper graphHopper;
    private final ExecutorService executorService;
    private final Timer requestLatency;
    private final Meter routeThroughput;

    @Inject
    public MatrixResource(GraphHopper graphHopper,
                          @Named(MatrixResourceBindings.EXECUTOR_BINDING) ExecutorService executorService,
                          MetricRegistry metrics) {
        this.graphHopper = Objects.requireNonNull(graphHopper, "graphHopper");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        Objects.requireNonNull(metrics, "metrics");
        this.requestLatency = metrics.timer("matrix.requests.latency");
        this.routeThroughput = metrics.meter("matrix.routes.per_second");
    }

    /**
     * Simple holder for binding names shared with HK2.
     */
    public static final class MatrixResourceBindings {
        public static final String EXECUTOR_BINDING = "matrix-executor";

        private MatrixResourceBindings() {
        }
    }

    @POST
    public Response compute(MatrixRequest request) {
        if (request == null) {
            throw badRequest("Request body must not be null");
        }

        Timer.Context timerContext = requestLatency.time();
        try {
            Profile profile = graphHopper.getProfile(request.getProfile());
            if (profile == null) {
                throw badRequest("Unknown profile: " + request.getProfile());
            }

            String profileName = profile.getName();
            RoutingCHGraph chGraph = graphHopper.getCHGraphs().get(profileName);
            LandmarkStorage landmarkStorage = graphHopper.getLandmarks().get(profileName);
            boolean chEnabled = chGraph != null;
            boolean lmEnabled = landmarkStorage != null;

            if (!chEnabled && !request.isEnableFallback()) {
                throw badRequest("CH not available for profile and fallback disabled");
            }

            List<List<Double>> points = request.getPoints();
            List<Integer> sources = request.getSources();
            List<Integer> targets = request.getTargets();
            validateMatrixSize(sources.size(), targets.size());

            long[][] distances = initializeMatrix(sources.size(), targets.size());
            long[][] times = initializeMatrix(sources.size(), targets.size());

            LocationIndex locationIndex = graphHopper.getLocationIndex();
            Snap[] snaps = snapPoints(points, locationIndex);
            List<Integer> failures = collectFailures(snaps);
            prefillFailures(sources, targets, failures, distances, times);

            Weighting weighting = graphHopper.createWeighting(profile, new PMap());
            AlgorithmOptions flexAlgoOpts = buildAlgorithmOptions(profile, graphHopper.getRouterConfig());
            PMap chHints = buildChHints(profile, graphHopper.getRouterConfig());
            RoutingAlgorithmFactory fallbackFactory = createFallbackFactory(lmEnabled, landmarkStorage);

            BaseGraph baseGraph = graphHopper.getBaseGraph();
            List<Callable<Void>> tasks = new ArrayList<>(sources.size());
            for (int rowIdx = 0; rowIdx < sources.size(); rowIdx++) {
                tasks.add(createRowTask(rowIdx, sources, targets, snaps, chEnabled, chGraph, weighting,
                        flexAlgoOpts, chHints, fallbackFactory, baseGraph, distances, times));
            }

            List<Future<Void>> futures = executorService.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }

            routeThroughput.mark((long) sources.size() * targets.size());
            MatrixResponse response = new MatrixResponse(distances, times, failures);
            return Response.ok(response).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebApplicationException(errorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Matrix computation interrupted"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new WebApplicationException(errorResponse(Response.Status.INTERNAL_SERVER_ERROR,
                    "Matrix computation failed: " + cause.getMessage()));
        } finally {
            timerContext.stop();
        }
    }

    private Callable<Void> createRowTask(
            int rowIdx,
            List<Integer> sources,
            List<Integer> targets,
            Snap[] snaps,
            boolean chEnabled,
            RoutingCHGraph chGraph,
            Weighting weighting,
            AlgorithmOptions flexAlgoOpts,
            PMap chHints,
            RoutingAlgorithmFactory fallbackFactory,
            BaseGraph baseGraph,
            long[][] distances,
            long[][] times) {

        return () -> {
            int sourcePointIndex = sources.get(rowIdx);
            Snap sourceSnap = snaps[sourcePointIndex];
            if (!sourceSnap.isValid()) {
                // already flagged as failure
                return null;
            }

            RowSnapContext snapContext = buildRowSnapContext(sourcePointIndex, targets, snaps);
            QueryGraph queryGraph = QueryGraph.create(baseGraph, snapContext.queryGraphSnaps());

            // CH/LM algorithms keep per-run state, so build a fresh instance per calcPath invocation.
            Supplier<RoutingAlgorithm> algoSupplier;
            if (chEnabled) {
                QueryRoutingCHGraph queryRoutingCHGraph = new QueryRoutingCHGraph(chGraph, queryGraph);
                CHRoutingAlgorithmFactory factory = new CHRoutingAlgorithmFactory(queryRoutingCHGraph);
                algoSupplier = () -> factory.createAlgo(chHints);
            } else {
                RoutingAlgorithmFactory factory = fallbackFactory;
                algoSupplier = () -> factory.createAlgo(queryGraph, weighting, flexAlgoOpts);
            }

            routeRow(algoSupplier, snapContext.sourceSnap(), snapContext.targetSnapsByIndex(), rowIdx, targets,
                    distances, times, snaps);
            return null;
        };
    }

    private RowSnapContext buildRowSnapContext(int sourcePointIndex, List<Integer> targets, Snap[] snaps) {
        Snap sourceClone = cloneSnap(snaps[sourcePointIndex]);
        List<Snap> queryGraphSnaps = new ArrayList<>(targets.size() + 1);
        queryGraphSnaps.add(sourceClone);

        Map<Integer, Snap> targetSnapMap = new HashMap<>();
        for (Integer targetIndex : targets) {
            Snap targetSnap = snaps[targetIndex];
            if (targetSnap.isValid() && !targetSnapMap.containsKey(targetIndex)) {
                Snap clone = cloneSnap(targetSnap);
                targetSnapMap.put(targetIndex, clone);
                queryGraphSnaps.add(clone);
            }
        }
        return new RowSnapContext(sourceClone, queryGraphSnaps, targetSnapMap);
    }

    private void routeRow(Supplier<RoutingAlgorithm> algoSupplier, Snap sourceSnap, Map<Integer, Snap> targetSnapsByIndex,
                          int rowIdx, List<Integer> targets, long[][] distances, long[][] times, Snap[] originalSnaps) {
        int sourceNode = sourceSnap.getClosestNode();
        for (int colIdx = 0; colIdx < targets.size(); colIdx++) {
            int targetPointIndex = targets.get(colIdx);
            Snap originalTarget = originalSnaps[targetPointIndex];
            if (originalTarget == null || !originalTarget.isValid()) {
                markUnreachable(rowIdx, colIdx, distances, times);
                continue;
            }
            Snap routedTarget = targetSnapsByIndex.get(targetPointIndex);
            if (routedTarget == null) {
                markUnreachable(rowIdx, colIdx, distances, times);
                continue;
            }
            int targetNode = routedTarget.getClosestNode();
            try {
                RoutingAlgorithm algo = algoSupplier.get();
                PathResult result = calcPath(algo, sourceNode, targetNode);
                distances[rowIdx][colIdx] = result.distance;
                times[rowIdx][colIdx] = result.time;
            } catch (ConnectionNotFoundException e) {
                markUnreachable(rowIdx, colIdx, distances, times);
            }
        }
    }

    private PathResult calcPath(RoutingAlgorithm algo, int sourceNode, int targetNode) {
        com.graphhopper.routing.Path path = algo.calcPath(sourceNode, targetNode);
        if (!path.isFound()) {
            return PathResult.unreachable();
        }
        return PathResult.of(Math.round(path.getDistance()), path.getTime());
    }

    private static long[][] initializeMatrix(int rows, int cols) {
        long[][] matrix = new long[rows][cols];
        for (long[] row : matrix) {
            Arrays.fill(row, -1);
        }
        return matrix;
    }

    private static void markUnreachable(int rowIdx, int colIdx, long[][] distances, long[][] times) {
        distances[rowIdx][colIdx] = -1;
        times[rowIdx][colIdx] = -1;
    }

    private static PMap buildChHints(Profile profile, com.graphhopper.routing.RouterConfig routerConfig) {
        PMap hints = new PMap(profile.getHints());
        hints.putObject(Parameters.Routing.ALGORITHM, Parameters.Algorithms.DIJKSTRA_BI);
        hints.putObject(Parameters.Routing.MAX_VISITED_NODES, routerConfig.getMaxVisitedNodes());
        hints.putObject(Parameters.Routing.TIMEOUT_MS, routerConfig.getTimeoutMillis());
        return hints;
    }

    private AlgorithmOptions buildAlgorithmOptions(Profile profile, com.graphhopper.routing.RouterConfig routerConfig) {
        AlgorithmOptions options = new AlgorithmOptions();
        options.setAlgorithm(Parameters.Algorithms.DIJKSTRA_BI);
        options.setTraversalMode(profile.hasTurnCosts() ? TraversalMode.EDGE_BASED : TraversalMode.NODE_BASED);
        options.setMaxVisitedNodes(routerConfig.getMaxVisitedNodes());
        options.setTimeoutMillis(routerConfig.getTimeoutMillis());
        options.setHints(new PMap(profile.getHints()));
        return options;
    }

    private RoutingAlgorithmFactory createFallbackFactory(boolean lmEnabled, LandmarkStorage landmarkStorage) {
        if (lmEnabled) {
            return new LMRoutingAlgorithmFactory(landmarkStorage)
                    .setDefaultActiveLandmarks(graphHopper.getRouterConfig().getActiveLandmarkCount());
        }
        return new RoutingAlgorithmFactorySimple();
    }

    private Snap cloneSnap(Snap original) {
        Snap clone = new Snap(original.getQueryPoint().getLat(), original.getQueryPoint().getLon());
        clone.setClosestNode(original.getClosestNode());
        clone.setClosestEdge(original.getClosestEdge());
        clone.setWayIndex(original.getWayIndex());
        clone.setSnappedPosition(original.getSnappedPosition());
        clone.setQueryDistance(original.getQueryDistance());
        try {
            GHPoint3D snappedPoint = original.getSnappedPoint();
            clone.setSnappedPoint(new GHPoint3D(snappedPoint.getLat(), snappedPoint.getLon(), snappedPoint.getEle()));
        } catch (IllegalStateException ignored) {
            // fall through; QueryGraph will recalculate if necessary
        }
        return clone;
    }

    private static void validateMatrixSize(int rows, int cols) {
        long cellCount = (long) rows * (long) cols;
        long maxCells = (long) MAX_DIMENSION * (long) MAX_DIMENSION;
        if (cellCount > maxCells) {
            throw new WebApplicationException(errorResponse(Response.Status.BAD_REQUEST, "Matrix too large"));
        }
    }

    private static Snap[] snapPoints(List<List<Double>> points, LocationIndex locationIndex) {
        Snap[] snaps = new Snap[points.size()];
        for (int i = 0; i < points.size(); i++) {
            List<Double> coord = points.get(i);
            double lat = coord.get(0);
            double lon = coord.get(1);
            snaps[i] = locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES);
        }
        return snaps;
    }

    private static List<Integer> collectFailures(Snap[] snaps) {
        List<Integer> failures = new ArrayList<>();
        for (int i = 0; i < snaps.length; i++) {
            if (!snaps[i].isValid()) {
                failures.add(i);
            }
        }
        return Collections.unmodifiableList(failures);
    }

    private static void prefillFailures(List<Integer> sources, List<Integer> targets, List<Integer> failures,
                                        long[][] distances, long[][] times) {
        Set<Integer> failureSet = failures.stream().collect(Collectors.toSet());
        for (int rowIdx = 0; rowIdx < sources.size(); rowIdx++) {
            if (failureSet.contains(sources.get(rowIdx))) {
                Arrays.fill(distances[rowIdx], -1);
                Arrays.fill(times[rowIdx], -1);
            }
        }
        for (int colIdx = 0; colIdx < targets.size(); colIdx++) {
            if (failureSet.contains(targets.get(colIdx))) {
                for (int row = 0; row < distances.length; row++) {
                    distances[row][colIdx] = -1;
                    times[row][colIdx] = -1;
                }
            }
        }
    }

    private static Response errorResponse(Response.Status status, String message) {
        return Response.status(status)
                .entity(Collections.singletonMap("message", message))
                .build();
    }

    private WebApplicationException badRequest(String message) {
        return new WebApplicationException(errorResponse(Response.Status.BAD_REQUEST, message));
    }

    private record RowSnapContext(Snap sourceSnap, List<Snap> queryGraphSnaps, Map<Integer, Snap> targetSnapsByIndex) {
    }

    private record PathResult(long distance, long time) {
        static PathResult unreachable() {
            return new PathResult(-1, -1);
        }

        static PathResult of(long distance, long time) {
            return new PathResult(distance, time);
        }
    }
}
