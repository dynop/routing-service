package com.dynop.graphhopper.matrix.config;

import com.codahale.metrics.MetricRegistry;
import com.dynop.graphhopper.matrix.api.MatrixResource;
import com.dynop.graphhopper.matrix.api.MatrixResource.MatrixResourceBindings;
import com.dynop.graphhopper.matrix.sea.*;
import com.graphhopper.GraphHopper;
import com.graphhopper.http.GraphHopperBundleConfiguration;
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
 * Dropwizard bundle that wires the shared matrix executor, routing registries, and JAX-RS resource.
 * 
 * <p>This bundle:
 * <ul>
 *   <li>Creates and manages the matrix computation thread pool</li>
 *   <li>Initializes the {@link RoutingEngineRegistry} with road and sea hoppers</li>
 *   <li>Loads the {@link ChokepointRegistry} for sea routing scenarios</li>
 *   <li>Loads the {@link UnlocodePortSnapper} for port coordinate snapping</li>
 *   <li>Registers all dependencies with HK2 for injection into resources</li>
 * </ul>
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
        
        // Get the shared GraphHopper instance (road hopper)
        GraphHopper roadHopper = null;
        if (configuration instanceof MatrixGraphHopperProvider provider) {
            try {
                roadHopper = provider.requireGraphHopper();
            } catch (Exception e) {
                LOGGER.warning("GraphHopper not available yet, will use lazy initialization");
            }
        }
        
        // Try to load sea hopper and related components
        GraphHopper seaHopper = loadSeaHopper(configuration);
        ChokepointRegistry chokepointRegistry = loadChokepointRegistry(configuration);
        UnlocodePortSnapper portSnapper = loadPortSnapper(configuration);
        
        // Create routing engine registry
        final GraphHopper finalRoadHopper = roadHopper;
        final RoutingEngineRegistry routingEngineRegistry = 
            finalRoadHopper != null 
                ? new RoutingEngineRegistry(finalRoadHopper, seaHopper)
                : null;

        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(executorService)
                        .to(ExecutorService.class)
                        .named(MatrixResourceBindings.EXECUTOR_BINDING);
                bind(metrics).to(MetricRegistry.class);
                
                if (routingEngineRegistry != null) {
                    bind(routingEngineRegistry).to(RoutingEngineRegistry.class);
                }
                if (chokepointRegistry != null) {
                    bind(chokepointRegistry).to(ChokepointRegistry.class);
                }
                if (portSnapper != null) {
                    bind(portSnapper).to(UnlocodePortSnapper.class);
                }
            }
        });

        environment.jersey().register(MatrixResource.class);
        
        LOGGER.info(() -> String.format(
            "MatrixBundle initialized: poolSize=%d, seaRouting=%s, chokepoints=%d, ports=%d",
            poolSize,
            seaHopper != null ? "enabled" : "disabled",
            chokepointRegistry != null ? chokepointRegistry.size() : 0,
            portSnapper != null ? portSnapper.getPortCount() : 0
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
            
            // Configure and load sea hopper
            GraphHopper seaHopper = new GraphHopper();
            seaHopper.setGraphHopperLocation(seaGraphLocation);
            seaHopper.load();
            
            LOGGER.info(() -> "Sea hopper loaded: " + seaHopper.getBaseGraph().getNodes() + " nodes");
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
