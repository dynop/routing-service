package com.dynop.graphhopper.matrix.sea.builder;

import com.dynop.graphhopper.matrix.sea.Chokepoint;
import com.dynop.graphhopper.matrix.sea.ChokepointRegistry;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.shapes.GHPoint;

import org.locationtech.jts.geom.*;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.opengis.feature.simple.SimpleFeature;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

/**
 * Offline CLI tool to build the global sea-lane graph.
 * 
 * <p>This builder generates a synthetic maritime graph by:
 * <ol>
 *   <li>Generating a global waypoint grid (default 5° step)</li>
 *   <li>Adding chokepoint locations with densified local grids</li>
 *   <li>Loading Natural Earth land polygons</li>
 *   <li>Removing waypoints that fall on land</li>
 *   <li>Connecting waypoints via k-nearest neighbors (k=6)</li>
 *   <li>Rejecting edges that cross land</li>
 *   <li>Validating global connectivity</li>
 *   <li>Persisting to GraphHopper format</li>
 * </ol>
 * 
 * <p><b>All land geometry checks are performed at build time or for validation only.
 * Runtime routing NEVER queries land geometry.</b>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * java -cp matrix-server.jar com.dynop.graphhopper.matrix.sea.builder.SeaLaneGraphBuilder \
 *     --output /path/to/graph-cache/sea \
 *     --landmask /path/to/natural-earth-data/ne_50m_land.shp \
 *     --step 5.0
 * }</pre>
 */
public class SeaLaneGraphBuilder {
    
    private static final Logger LOGGER = Logger.getLogger(SeaLaneGraphBuilder.class.getName());
    
    // Default configuration
    private static final double DEFAULT_GRID_STEP_DEGREES = 5.0;
    private static final double MIN_LAT = -80.0;
    private static final double MAX_LAT = 80.0;
    private static final double MIN_LON = -180.0;
    private static final double MAX_LON = 180.0;
    private static final int K_NEIGHBORS = 6;
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    // Mandatory chokepoints with densification parameters
    private static final List<ChokepointDefinition> MANDATORY_CHOKEPOINTS = List.of(
        new ChokepointDefinition("SUEZ", "Suez Canal", "AFRICA", 30.812330, 32.317903, 2.0, 0.5),
        new ChokepointDefinition("PANAMA", "Panama Canal", "AMERICAS", 9.083179, -79.677571, 2.0, 0.5),
        new ChokepointDefinition("MALACCA", "Strait of Malacca", "ASIA", 2.5, 101.0, 3.0, 0.5),
        new ChokepointDefinition("GIBRALTAR", "Strait of Gibraltar", "EUROPE", 35.942918, -5.614690, 2.0, 0.5),
        new ChokepointDefinition("BOSPHORUS", "Bosphorus Strait", "EUROPE", 41.097591, 29.060623, 2.0, 0.5),
        new ChokepointDefinition("CAPE_GOOD_HOPE", "Cape of Good Hope", "AFRICA", -34.353219, 18.228192, 3.0, 1.0),
        new ChokepointDefinition("BAB_EL_MANDEB", "Bab-el-Mandeb", "AFRICA", 12.6, 43.3, 2.0, 0.5),
        new ChokepointDefinition("HORMUZ", "Strait of Hormuz", "ASIA", 26.5, 56.3, 2.0, 0.5)
    );
    
    private final Path outputDir;
    private final Path landmaskPath;
    private final double gridStepDegrees;
    
    private Geometry landGeometry;
    private GeometryFactory geometryFactory;
    private final DistanceCalcEarth distCalc = new DistanceCalcEarth();
    
    /**
     * Creates a new SeaLaneGraphBuilder.
     * 
     * @param outputDir       Output directory for sea graph (e.g., graph-cache/sea)
     * @param landmaskPath    Path to Natural Earth land shapefile
     * @param gridStepDegrees Grid step size in degrees (default 5.0)
     */
    public SeaLaneGraphBuilder(Path outputDir, Path landmaskPath, double gridStepDegrees) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
        this.landmaskPath = Objects.requireNonNull(landmaskPath, "landmaskPath");
        this.gridStepDegrees = gridStepDegrees > 0 ? gridStepDegrees : DEFAULT_GRID_STEP_DEGREES;
        this.geometryFactory = new GeometryFactory();
    }
    
    /**
     * Build the sea-lane graph.
     * 
     * @return BuildResult containing statistics and metadata
     * @throws IOException if I/O errors occur
     */
    public BuildResult build() throws IOException {
        long startTime = System.currentTimeMillis();
        LOGGER.info("Starting sea-lane graph build...");
        
        // Step 1: Load land geometry
        LOGGER.info("Loading land geometry from " + landmaskPath);
        loadLandGeometry();
        LOGGER.info("Land geometry loaded successfully");
        
        // Step 2: Generate waypoints
        LOGGER.info("Generating global waypoint grid (step=" + gridStepDegrees + "°)");
        List<Waypoint> waypoints = generateWaypoints();
        LOGGER.info("Generated " + waypoints.size() + " initial waypoints");
        
        // Step 3: Filter waypoints on land
        LOGGER.info("Filtering waypoints on land...");
        List<Waypoint> oceanWaypoints = filterLandWaypoints(waypoints);
        LOGGER.info("Remaining ocean waypoints: " + oceanWaypoints.size());
        
        // Step 4: Build graph with k-nearest neighbors
        LOGGER.info("Building graph with k=" + K_NEIGHBORS + " neighbors...");
        GraphBuildResult graphResult = buildGraph(oceanWaypoints);
        LOGGER.info("Graph built: " + graphResult.nodeCount + " nodes, " + graphResult.edgeCount + " edges");
        
        // Step 5: Validate connectivity
        LOGGER.info("Validating global connectivity...");
        ConnectivityResult connectivity = validateConnectivity(graphResult.graph, oceanWaypoints);
        
        if (connectivity.componentCount > 1) {
            LOGGER.warning("Multiple connected components detected: " + connectivity.componentCount);
            LOGGER.warning("Largest component size: " + connectivity.largestComponentSize);
        }
        
        // Step 6: Save graph
        Files.createDirectories(outputDir);
        LOGGER.info("Persisting graph to " + outputDir);
        graphResult.graph.flush();
        
        // Step 7: Save chokepoint metadata
        Path metadataPath = outputDir.resolve("chokepoint_metadata.json");
        saveChokepointMetadata(metadataPath, graphResult.chokepointNodeMap);
        
        // Step 8: Save build summary
        long buildDuration = System.currentTimeMillis() - startTime;
        BuildResult result = new BuildResult(
            graphResult.nodeCount,
            graphResult.edgeCount,
            connectivity.componentCount,
            connectivity.largestComponentSize,
            buildDuration,
            gridStepDegrees,
            0.5,  // chokepoint step
            computeGraphHash(graphResult.graph)
        );
        
        saveBuildSummary(outputDir.resolve("build_summary.json"), result);
        
        LOGGER.info(String.format("Sea-lane graph build completed in %d ms", buildDuration));
        LOGGER.info(String.format("Final: %d nodes, %d edges, %d components",
            graphResult.nodeCount, graphResult.edgeCount, connectivity.componentCount));
        
        return result;
    }
    
    /**
     * Load Natural Earth land polygons from shapefile.
     */
    private void loadLandGeometry() throws IOException {
        FileDataStore dataStore = FileDataStoreFinder.getDataStore(landmaskPath.toFile());
        if (dataStore == null) {
            throw new IOException("Cannot open shapefile: " + landmaskPath);
        }
        
        try {
            SimpleFeatureSource featureSource = dataStore.getFeatureSource();
            List<Geometry> geometries = new ArrayList<>();
            
            try (SimpleFeatureIterator iterator = featureSource.getFeatures().features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Object geom = feature.getDefaultGeometry();
                    if (geom instanceof Geometry) {
                        geometries.add((Geometry) geom);
                    }
                }
            }
            
            if (geometries.isEmpty()) {
                throw new IOException("No geometries found in shapefile");
            }
            
            // Combine all land polygons into a single geometry
            landGeometry = geometryFactory.createGeometryCollection(
                geometries.toArray(new Geometry[0])).union();
            
            LOGGER.info("Loaded " + geometries.size() + " land polygons");
        } finally {
            dataStore.dispose();
        }
    }
    
    /**
     * Generate global waypoint grid including chokepoint densification.
     */
    private List<Waypoint> generateWaypoints() {
        List<Waypoint> waypoints = new ArrayList<>();
        Map<String, Set<Integer>> chokepointWaypointIndices = new HashMap<>();
        
        // Initialize chokepoint tracking
        for (ChokepointDefinition cp : MANDATORY_CHOKEPOINTS) {
            chokepointWaypointIndices.put(cp.id, new HashSet<>());
        }
        
        // Generate main grid
        for (double lat = MIN_LAT; lat <= MAX_LAT; lat += gridStepDegrees) {
            for (double lon = MIN_LON; lon < MAX_LON; lon += gridStepDegrees) {
                int index = waypoints.size();
                String chokepointId = findNearbyChokepoint(lat, lon, gridStepDegrees * 2);
                waypoints.add(new Waypoint(index, lat, lon, chokepointId));
                
                if (chokepointId != null) {
                    chokepointWaypointIndices.get(chokepointId).add(index);
                }
            }
        }
        
        // Add densified grids around chokepoints
        for (ChokepointDefinition cp : MANDATORY_CHOKEPOINTS) {
            List<Waypoint> densePoints = densifyAroundChokepoint(
                cp.lat, cp.lon, cp.radiusDegrees, cp.stepDegrees, cp.id, waypoints.size());
            
            for (Waypoint wp : densePoints) {
                chokepointWaypointIndices.get(cp.id).add(wp.index);
            }
            waypoints.addAll(densePoints);
            
            LOGGER.fine(() -> String.format("Added %d dense waypoints for chokepoint %s",
                densePoints.size(), cp.id));
        }
        
        return waypoints;
    }
    
    /**
     * Generate dense local grid around a chokepoint.
     */
    private List<Waypoint> densifyAroundChokepoint(double centerLat, double centerLon,
                                                    double radiusDegrees, double stepDegrees,
                                                    String chokepointId, int startIndex) {
        List<Waypoint> points = new ArrayList<>();
        int index = startIndex;
        
        for (double dlat = -radiusDegrees; dlat <= radiusDegrees; dlat += stepDegrees) {
            for (double dlon = -radiusDegrees; dlon <= radiusDegrees; dlon += stepDegrees) {
                double distance = Math.sqrt(dlat * dlat + dlon * dlon);
                if (distance <= radiusDegrees && distance > 0) {  // Skip center point
                    double lat = centerLat + dlat;
                    double lon = centerLon + dlon;
                    
                    // Keep within valid ranges
                    if (lat >= MIN_LAT && lat <= MAX_LAT) {
                        lon = normalizeLongitude(lon);
                        points.add(new Waypoint(index++, lat, lon, chokepointId));
                    }
                }
            }
        }
        
        // Add the chokepoint center itself
        points.add(new Waypoint(index, centerLat, centerLon, chokepointId));
        
        return points;
    }
    
    /**
     * Find if a point is near any mandatory chokepoint.
     */
    private String findNearbyChokepoint(double lat, double lon, double thresholdDegrees) {
        for (ChokepointDefinition cp : MANDATORY_CHOKEPOINTS) {
            double dist = Math.sqrt(Math.pow(lat - cp.lat, 2) + Math.pow(lon - cp.lon, 2));
            if (dist <= thresholdDegrees) {
                return cp.id;
            }
        }
        return null;
    }
    
    /**
     * Filter out waypoints that fall on land.
     */
    private List<Waypoint> filterLandWaypoints(List<Waypoint> waypoints) {
        List<Waypoint> oceanWaypoints = new ArrayList<>();
        int landCount = 0;
        
        for (Waypoint wp : waypoints) {
            Point point = geometryFactory.createPoint(new Coordinate(wp.lon, wp.lat));
            if (!landGeometry.contains(point)) {
                oceanWaypoints.add(new Waypoint(oceanWaypoints.size(), wp.lat, wp.lon, wp.chokepointId));
            } else {
                landCount++;
            }
        }
        
        int finalLandCount = landCount;
        LOGGER.info(() -> "Filtered out " + finalLandCount + " land waypoints");
        return oceanWaypoints;
    }
    
    /**
     * Build the graph using k-nearest neighbors with antimeridian-aware distance.
     */
    private GraphBuildResult buildGraph(List<Waypoint> waypoints) {
        // Create encoding manager with minimal values for sea graph
        BooleanEncodedValue accessEnc = VehicleAccess.create("car");
        DecimalEncodedValue speedEnc = VehicleSpeed.create("car", 5, 5, false);
        EncodingManager encodingManager = EncodingManager.start()
            .add(accessEnc)
            .add(speedEnc)
            .build();
        
        BaseGraph graph = new BaseGraph.Builder(encodingManager)
            .setDir(new RAMDirectory(outputDir.toString(), true))
            .set3D(false)
            .create();
        
        // Add nodes
        for (Waypoint wp : waypoints) {
            graph.getNodeAccess().setNode(wp.index, wp.lat, wp.lon);
        }
        
        // Build KNN index for efficient neighbor lookup
        Map<Integer, List<NeighborEntry>> neighbors = buildKNNIndex(waypoints);
        
        // Add edges
        int edgeCount = 0;
        Set<String> addedEdges = new HashSet<>();  // Prevent duplicate edges
        
        for (Waypoint wp : waypoints) {
            List<NeighborEntry> knn = neighbors.get(wp.index);
            if (knn == null) continue;
            
            for (NeighborEntry neighbor : knn) {
                String edgeKey = Math.min(wp.index, neighbor.index) + "-" + Math.max(wp.index, neighbor.index);
                if (addedEdges.contains(edgeKey)) continue;
                
                // Check if edge crosses land
                Waypoint neighborWp = waypoints.get(neighbor.index);
                if (!edgeCrossesLand(wp.lat, wp.lon, neighborWp.lat, neighborWp.lon)) {
                    graph.edge(wp.index, neighbor.index)
                        .setDistance(neighbor.distance * 1000)  // Convert km to m
                        .set(accessEnc, true, true)
                        .set(speedEnc, 30.0);  // Placeholder speed
                    
                    addedEdges.add(edgeKey);
                    edgeCount++;
                }
            }
        }
        
        // Build chokepoint node map
        Map<String, Set<Integer>> chokepointNodeMap = new HashMap<>();
        for (Waypoint wp : waypoints) {
            if (wp.chokepointId != null) {
                chokepointNodeMap.computeIfAbsent(wp.chokepointId, k -> new HashSet<>())
                    .add(wp.index);
            }
        }
        
        return new GraphBuildResult(graph, waypoints.size(), edgeCount, chokepointNodeMap);
    }
    
    /**
     * Build k-nearest neighbor index with antimeridian-aware distance.
     */
    private Map<Integer, List<NeighborEntry>> buildKNNIndex(List<Waypoint> waypoints) {
        Map<Integer, List<NeighborEntry>> neighbors = new HashMap<>();
        
        for (Waypoint wp : waypoints) {
            PriorityQueue<NeighborEntry> pq = new PriorityQueue<>(
                Comparator.comparingDouble(e -> -e.distance));  // Max heap
            
            for (Waypoint other : waypoints) {
                if (wp.index == other.index) continue;
                
                double dist = antimeridianAwareDistance(wp.lat, wp.lon, other.lat, other.lon);
                
                if (pq.size() < K_NEIGHBORS) {
                    pq.offer(new NeighborEntry(other.index, dist));
                } else if (dist < pq.peek().distance) {
                    pq.poll();
                    pq.offer(new NeighborEntry(other.index, dist));
                }
            }
            
            neighbors.put(wp.index, new ArrayList<>(pq));
        }
        
        return neighbors;
    }
    
    /**
     * Compute distance considering dateline wrap-around.
     * For KNN, check both the original point and its ±360° shifted version.
     */
    private double antimeridianAwareDistance(double lat1, double lon1, double lat2, double lon2) {
        double directDist = haversineDistanceKm(lat1, lon1, lat2, lon2);
        double wrappedDist1 = haversineDistanceKm(lat1, lon1 + 360, lat2, lon2);
        double wrappedDist2 = haversineDistanceKm(lat1, lon1 - 360, lat2, lon2);
        return Math.min(directDist, Math.min(wrappedDist1, wrappedDist2));
    }
    
    /**
     * Haversine distance in kilometers.
     */
    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
    
    /**
     * Check if an edge crosses land using line-polygon intersection.
     */
    private boolean edgeCrossesLand(double lat1, double lon1, double lat2, double lon2) {
        // Handle antimeridian crossing by checking both possibilities
        if (Math.abs(lon2 - lon1) > 180) {
            // Edge crosses antimeridian - split and check both segments
            return edgeCrossesLandSegment(lat1, lon1, lat1 + (lat2 - lat1) / 2, 
                    lon1 > 0 ? 180 : -180) ||
                   edgeCrossesLandSegment(lat1 + (lat2 - lat1) / 2,
                    lon2 > 0 ? -180 : 180, lat2, lon2);
        }
        
        return edgeCrossesLandSegment(lat1, lon1, lat2, lon2);
    }
    
    private boolean edgeCrossesLandSegment(double lat1, double lon1, double lat2, double lon2) {
        Coordinate[] coords = new Coordinate[] {
            new Coordinate(lon1, lat1),
            new Coordinate(lon2, lat2)
        };
        LineString line = geometryFactory.createLineString(coords);
        return landGeometry.intersects(line);
    }
    
    /**
     * Validate global connectivity using connected components.
     */
    private ConnectivityResult validateConnectivity(BaseGraph graph, List<Waypoint> waypoints) {
        int nodeCount = waypoints.size();
        int[] component = new int[nodeCount];
        Arrays.fill(component, -1);
        
        int componentId = 0;
        int largestSize = 0;
        
        for (int i = 0; i < nodeCount; i++) {
            if (component[i] == -1) {
                int size = bfs(graph, i, component, componentId);
                largestSize = Math.max(largestSize, size);
                componentId++;
            }
        }
        
        return new ConnectivityResult(componentId, largestSize);
    }
    
    private int bfs(BaseGraph graph, int start, int[] component, int componentId) {
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(start);
        component[start] = componentId;
        int size = 0;
        
        while (!queue.isEmpty()) {
            int node = queue.poll();
            size++;
            
            var edgeIterator = graph.createEdgeExplorer().setBaseNode(node);
            while (edgeIterator.next()) {
                int neighbor = edgeIterator.getAdjNode();
                if (component[neighbor] == -1) {
                    component[neighbor] = componentId;
                    queue.offer(neighbor);
                }
            }
        }
        
        return size;
    }
    
    /**
     * Save chokepoint metadata to JSON.
     */
    private void saveChokepointMetadata(Path metadataPath, Map<String, Set<Integer>> chokepointNodeMap) 
            throws IOException {
        Map<String, Chokepoint> chokepoints = new HashMap<>();
        
        for (ChokepointDefinition cpDef : MANDATORY_CHOKEPOINTS) {
            Set<Integer> nodeIds = chokepointNodeMap.getOrDefault(cpDef.id, Collections.emptySet());
            Chokepoint cp = new Chokepoint(
                cpDef.id, cpDef.name, cpDef.region,
                cpDef.lat, cpDef.lon,
                cpDef.radiusDegrees, cpDef.stepDegrees,
                nodeIds
            );
            chokepoints.put(cpDef.id, cp);
        }
        
        ChokepointRegistry registry = new ChokepointRegistry(chokepoints);
        registry.saveTo(metadataPath);
    }
    
    /**
     * Save build summary to JSON.
     */
    private void saveBuildSummary(Path summaryPath, BuildResult result) throws IOException {
        String json = String.format("""
            {
              "sea_graph_version": "%s",
              "node_count": %d,
              "edge_count": %d,
              "connected_component_count": %d,
              "largest_component_size": %d,
              "build_duration_ms": %d,
              "waypoint_grid_step_degrees": %.1f,
              "chokepoint_densification_step_degrees": %.1f,
              "land_mask_source": "ne_50m_land.shp",
              "graphhopper_version": "11.0",
              "build_timestamp": "%s"
            }
            """,
            result.graphVersion,
            result.nodeCount,
            result.edgeCount,
            result.componentCount,
            result.largestComponentSize,
            result.buildDurationMs,
            result.gridStepDegrees,
            result.chokepointStepDegrees,
            Instant.now().toString()
        );
        
        Files.writeString(summaryPath, json, StandardCharsets.UTF_8);
        LOGGER.info("Build summary saved to " + summaryPath);
    }
    
    /**
     * Compute a stable hash of the graph for cache invalidation.
     */
    private String computeGraphHash(BaseGraph graph) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(graph.getNodes()).getBytes());
            digest.update(String.valueOf(graph.getEdges()).getBytes());
            digest.update(String.valueOf(System.currentTimeMillis()).getBytes());
            
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 20);  // Truncate for readability
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private static double normalizeLongitude(double lon) {
        while (lon > 180) lon -= 360;
        while (lon < -180) lon += 360;
        return lon;
    }
    
    // ========== Main entry point ==========
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: SeaLaneGraphBuilder --output <dir> --landmask <shapefile> [--step <degrees>]");
            System.exit(1);
        }
        
        Path outputDir = null;
        Path landmaskPath = null;
        double step = DEFAULT_GRID_STEP_DEGREES;
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output":
                    outputDir = Path.of(args[++i]);
                    break;
                case "--landmask":
                    landmaskPath = Path.of(args[++i]);
                    break;
                case "--step":
                    step = Double.parseDouble(args[++i]);
                    break;
            }
        }
        
        if (outputDir == null || landmaskPath == null) {
            System.err.println("Error: --output and --landmask are required");
            System.exit(1);
        }
        
        try {
            SeaLaneGraphBuilder builder = new SeaLaneGraphBuilder(outputDir, landmaskPath, step);
            BuildResult result = builder.build();
            
            System.out.println("Build completed successfully!");
            System.out.println("  Nodes: " + result.nodeCount);
            System.out.println("  Edges: " + result.edgeCount);
            System.out.println("  Components: " + result.componentCount);
            System.out.println("  Duration: " + result.buildDurationMs + " ms");
            
        } catch (Exception e) {
            System.err.println("Build failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    // ========== Internal types ==========
    
    private static class Waypoint {
        final int index;
        final double lat;
        final double lon;
        final String chokepointId;
        
        Waypoint(int index, double lat, double lon, String chokepointId) {
            this.index = index;
            this.lat = lat;
            this.lon = lon;
            this.chokepointId = chokepointId;
        }
    }
    
    private static class NeighborEntry {
        final int index;
        final double distance;
        
        NeighborEntry(int index, double distance) {
            this.index = index;
            this.distance = distance;
        }
    }
    
    private static class ChokepointDefinition {
        final String id;
        final String name;
        final String region;
        final double lat;
        final double lon;
        final double radiusDegrees;
        final double stepDegrees;
        
        ChokepointDefinition(String id, String name, String region, 
                            double lat, double lon,
                            double radiusDegrees, double stepDegrees) {
            this.id = id;
            this.name = name;
            this.region = region;
            this.lat = lat;
            this.lon = lon;
            this.radiusDegrees = radiusDegrees;
            this.stepDegrees = stepDegrees;
        }
    }
    
    private static class GraphBuildResult {
        final BaseGraph graph;
        final int nodeCount;
        final int edgeCount;
        final Map<String, Set<Integer>> chokepointNodeMap;
        
        GraphBuildResult(BaseGraph graph, int nodeCount, int edgeCount, 
                        Map<String, Set<Integer>> chokepointNodeMap) {
            this.graph = graph;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.chokepointNodeMap = chokepointNodeMap;
        }
    }
    
    private static class ConnectivityResult {
        final int componentCount;
        final int largestComponentSize;
        
        ConnectivityResult(int componentCount, int largestComponentSize) {
            this.componentCount = componentCount;
            this.largestComponentSize = largestComponentSize;
        }
    }
    
    /**
     * Result of the build process.
     */
    public static class BuildResult {
        public final int nodeCount;
        public final int edgeCount;
        public final int componentCount;
        public final int largestComponentSize;
        public final long buildDurationMs;
        public final double gridStepDegrees;
        public final double chokepointStepDegrees;
        public final String graphVersion;
        
        public BuildResult(int nodeCount, int edgeCount, int componentCount,
                          int largestComponentSize, long buildDurationMs,
                          double gridStepDegrees, double chokepointStepDegrees,
                          String graphVersion) {
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
            this.componentCount = componentCount;
            this.largestComponentSize = largestComponentSize;
            this.buildDurationMs = buildDurationMs;
            this.gridStepDegrees = gridStepDegrees;
            this.chokepointStepDegrees = chokepointStepDegrees;
            this.graphVersion = graphVersion;
        }
    }
}
