package com.dynop.graphhopper.matrix.config;

import com.codahale.metrics.MetricRegistry;
import com.dynop.graphhopper.matrix.api.MatrixResource;
import com.dynop.graphhopper.matrix.api.MatrixResource.MatrixResourceBindings;
import com.dynop.graphhopper.matrix.sea.*;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperBundleConfiguration;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dropwizard bundle that wires the shared matrix executor, sea routing components, and JAX-RS resource.
 * 
 * <p>This bundle:
 * <ul>
 *   <li>Creates and manages the matrix computation thread pool</li>
 *   <li>Loads the optional sea hopper for maritime routing</li>
 *   <li>Loads the {@link ChokepointRegistry} for sea routing scenarios</li>
 *   <li>Loads the {@link UnlocodePortSnapper} for port coordinate snapping</li>
 *   <li>Registers all dependencies with HK2 for injection into resources</li>
 * </ul>
 * 
 * <p>Road routing uses the GraphHopper instance provided by GraphHopperBundle.
 */
public class MatrixBundle implements ConfiguredBundle<GraphHopperBundleConfiguration> {

    private static final Logger LOGGER = Logger.getLogger(MatrixBundle.class.getName());

    @Override
    public void initialize(Bootstrap<?> bootstrap) {
        // no-op
    }

    @Override
    public void run(GraphHopperBundleConfiguration configuration, Environment environment) {
        int poolSize = resolvePoolSize(configuration);
        AtomicInteger threadCounter = new AtomicInteger(1);
        ExecutorService executorService = Executors.newFixedThreadPool(poolSize, r -> {
            Thread thread = new Thread(r, "matrix-worker-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });

        environment.lifecycle().manage(new ManagedExecutor(executorService));
        MetricRegistry metrics = environment.metrics();
        
        // Road hopper is provided by GraphHopperBundle and injected directly into MatrixResource
        // We only need to set up the optional sea routing components here
        
        // Try to load sea hopper and related components
        GraphHopper seaHopper = loadSeaHopper(configuration);
        ChokepointRegistry chokepointRegistry = loadChokepointRegistry(configuration);
        UnlocodePortSnapper portSnapper = loadPortSnapper(configuration);
        
        // Ensure chokepoint registry is never null
        final ChokepointRegistry finalChokepointRegistry = 
            chokepointRegistry != null ? chokepointRegistry : new ChokepointRegistry();
        
        // Ensure port snapper is never null (empty port list means no snapping available)
        final UnlocodePortSnapper finalPortSnapper = 
            portSnapper != null ? portSnapper : new UnlocodePortSnapper(List.of());
        
        // Store sea hopper reference for injection
        final SeaHopperHolder seaHopperHolder = new SeaHopperHolder(seaHopper);

        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(executorService)
                        .to(ExecutorService.class)
                        .named(MatrixResourceBindings.EXECUTOR_BINDING);
                bind(metrics).to(MetricRegistry.class);
                
                // Bind sea routing components (always bind, even when disabled)
                bind(seaHopperHolder).to(SeaHopperHolder.class);
                bind(finalChokepointRegistry).to(ChokepointRegistry.class);
                bind(finalPortSnapper).to(UnlocodePortSnapper.class);
            }
        });

        environment.jersey().register(MatrixResource.class);
        
        LOGGER.info(() -> String.format(
            "MatrixBundle initialized: poolSize=%d, seaRouting=%s, chokepoints=%d, ports=%d",
            poolSize,
            seaHopper != null ? "enabled" : "disabled",
            finalChokepointRegistry.size(),
            finalPortSnapper.getPortCount()
        ));
    }

    private int resolvePoolSize(GraphHopperBundleConfiguration configuration) {
        int defaultSize = Runtime.getRuntime().availableProcessors();
        try {
            int poolSize = configuration.getGraphHopperConfiguration()
                    .getInt("matrix.executor.pool_size", defaultSize);
            return poolSize > 0 ? poolSize : defaultSize;
        } catch (Exception e) {
            return defaultSize;
        }
    }
    
    /**
     * Try to load the sea GraphHopper instance from the sea graph cache.
     */
    private GraphHopper loadSeaHopper(GraphHopperBundleConfiguration configuration) {
        try {
            String seaGraphLocation = configuration.getGraphHopperConfiguration()
                    .getString("sea.graph.location", null);
            
            if (seaGraphLocation == null) {
                // Try default location relative to road graph
                String roadLocation = configuration.getGraphHopperConfiguration()
                        .getString("graph.location", "graph-cache");
                seaGraphLocation = Path.of(roadLocation).getParent().resolve("sea").toString();
            }
            
            Path seaGraphPath = Path.of(seaGraphLocation);
            if (!Files.exists(seaGraphPath) || !Files.isDirectory(seaGraphPath)) {
                LOGGER.info(() -> "Sea graph not found at " + seaGraphPath + ", sea routing disabled");
                return null;
            }
            
            // Check for required files
            if (!Files.exists(seaGraphPath.resolve("nodes")) || 
                !Files.exists(seaGraphPath.resolve("edges"))) {
                LOGGER.info(() -> "Sea graph incomplete at " + seaGraphPath + ", sea routing disabled");
                return null;
            }
            
            LOGGER.info(() -> "Sea graph found at " + seaGraphPath + ", loading...");
            
            // Load the pre-built sea graph directly as a BaseGraph
            // The sea graph was built with SeaLaneGraphBuilder which creates a BaseGraph
            // and flushes it to disk using RAMDirectory. We need to load it the same way.
            GraphHopper seaHopper = new GraphHopper();
            seaHopper.setGraphHopperLocation(seaGraphLocation);
            
            // Add a ship profile BEFORE creating encoding manager
            // Use custom weighting with a simple distance-based model
            com.graphhopper.config.Profile shipProfile = new com.graphhopper.config.Profile("ship");
            shipProfile.setWeighting("custom");
            
            // Create a simple custom model for distance-based routing
            com.graphhopper.util.CustomModel customModel = new com.graphhopper.util.CustomModel();
            customModel.setDistanceInfluence(100.0); // Pure distance-based routing
            customModel.addToSpeed(com.graphhopper.json.Statement.If("true", com.graphhopper.json.Statement.Op.LIMIT, "100"));
            
            shipProfile.setCustomModel(customModel);
            seaHopper.setProfiles(shipProfile);
            
            // Create the encoding manager matching what the builder used
            BooleanEncodedValue accessEnc = com.graphhopper.routing.ev.VehicleAccess.create("car");
            DecimalEncodedValue speedEnc = com.graphhopper.routing.ev.VehicleSpeed.create("car", 5, 5, false);
            EncodingManager encodingManager = EncodingManager.start()
                .add(accessEnc)
                .add(speedEnc)
                .build();
            
            // Load the base graph directly from disk (no .create(), just loadExisting)
            BaseGraph baseGraph = new BaseGraph.Builder(encodingManager)
                .setDir(new com.graphhopper.storage.RAMDirectory(seaGraphLocation, true))
                .set3D(false)
                .build();  // Use build() instead of create()
            
            baseGraph.loadExisting();
            
            // Inject the loaded graph and encoding manager into seaHopper using reflection
            try {
                java.lang.reflect.Field baseGraphField = GraphHopper.class.getDeclaredField("baseGraph");
                baseGraphField.setAccessible(true);
                baseGraphField.set(seaHopper, baseGraph);
                
                // Also inject the encoding manager
                java.lang.reflect.Field encodingManagerField = GraphHopper.class.getDeclaredField("encodingManager");
                encodingManagerField.setAccessible(true);
                encodingManagerField.set(seaHopper, encodingManager);
                
                // Load or create the location index for spatial queries
                // For sea graph with 5° grid (~555km spacing), we need a large search radius
                LocationIndexTree locationIndex;
                com.graphhopper.storage.RAMDirectory indexDir = new com.graphhopper.storage.RAMDirectory(seaGraphLocation, true);
                if (Files.exists(seaGraphPath.resolve("location_index"))) {
                    // Load existing location index
                    locationIndex = new LocationIndexTree(baseGraph, indexDir);
                    locationIndex.setMaxRegionSearch(512); // Very large search radius for coarse sea grid (5° = ~555km)
                    if (!locationIndex.loadExisting()) {
                        // If loading fails, prepare a new one
                        locationIndex.prepareIndex();
                    }
                } else {
                    // Create new location index
                    locationIndex = new LocationIndexTree(baseGraph, indexDir);
                    locationIndex.setMaxRegionSearch(512); // Very large search radius for coarse sea grid (5° = ~555km)
                    locationIndex.prepareIndex();
                }
                
                // Inject location index
                java.lang.reflect.Field locationIndexField = GraphHopper.class.getDeclaredField("locationIndex");
                locationIndexField.setAccessible(true);
                locationIndexField.set(seaHopper, locationIndex);
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to inject BaseGraph into GraphHopper", e);
            }
            
            int nodeCount = baseGraph.getNodes();
            LOGGER.info(() -> "Sea hopper loaded: " + nodeCount + " nodes");
            return seaHopper;
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load sea hopper: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Load chokepoint registry from the sea graph cache.
     */
    private ChokepointRegistry loadChokepointRegistry(GraphHopperBundleConfiguration configuration) {
        try {
            String seaGraphLocation = configuration.getGraphHopperConfiguration()
                    .getString("sea.graph.location", null);
            
            if (seaGraphLocation == null) {
                String roadLocation = configuration.getGraphHopperConfiguration()
                        .getString("graph.location", "graph-cache");
                seaGraphLocation = Path.of(roadLocation).getParent().resolve("sea").toString();
            }
            
            Path metadataPath = Path.of(seaGraphLocation).resolve("chokepoint_metadata.json");
            if (!Files.exists(metadataPath)) {
                LOGGER.info(() -> "Chokepoint metadata not found at " + metadataPath);
                return new ChokepointRegistry();
            }
            
            return ChokepointRegistry.loadFrom(metadataPath);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load chokepoint registry: " + e.getMessage(), e);
            return new ChokepointRegistry();
        }
    }
    
    /**
     * Load port snapper from UN/LOCODE data.
     */
    private UnlocodePortSnapper loadPortSnapper(GraphHopperBundleConfiguration configuration) {
        try {
            String unlocodeDir = configuration.getGraphHopperConfiguration()
                    .getString("sea.unlocode.directory", "unlocode-data");
            
            Path unlocodePath = Path.of(unlocodeDir);
            if (!Files.exists(unlocodePath)) {
                LOGGER.info(() -> "UN/LOCODE directory not found at " + unlocodePath);
                return null;
            }
            
            // Find all UNLOCODE CSV files
            Path[] csvFiles = Files.list(unlocodePath)
                    .filter(p -> p.getFileName().toString().contains("UNLOCODE") && 
                                 p.getFileName().toString().endsWith(".csv"))
                    .toArray(Path[]::new);
            
            if (csvFiles.length == 0) {
                LOGGER.info(() -> "No UN/LOCODE CSV files found in " + unlocodePath);
                return null;
            }
            
            UnlocodePortLoader loader = new UnlocodePortLoader();
            List<Port> ports = loader.loadSeaports(csvFiles);
            
            double maxSnapDistance = configuration.getGraphHopperConfiguration()
                    .getDouble("sea.port_snapping.max_snap_distance_km", 300.0);
            
            return new UnlocodePortSnapper(ports, maxSnapDistance);
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load port snapper: " + e.getMessage(), e);
            return null;
        }
    }

    private static final class ManagedExecutor implements io.dropwizard.lifecycle.Managed {
        private final ExecutorService delegate;

        private ManagedExecutor(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start() {
            LOGGER.info(() -> "Matrix executor started with " + Runtime.getRuntime().availableProcessors() + " workers");
        }

        @Override
        public void stop() {
            delegate.shutdown();
            try {
                if (!delegate.awaitTermination(30, TimeUnit.SECONDS)) {
                    delegate.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                delegate.shutdownNow();
            }
            LOGGER.info("Matrix executor stopped");
        }
    }
}
