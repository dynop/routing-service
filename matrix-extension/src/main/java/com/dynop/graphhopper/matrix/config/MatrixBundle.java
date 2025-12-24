package com.dynop.graphhopper.matrix.config;

import com.codahale.metrics.MetricRegistry;
import com.dynop.graphhopper.matrix.api.MatrixResource;
import com.dynop.graphhopper.matrix.api.MatrixResource.MatrixResourceBindings;
import com.graphhopper.http.GraphHopperBundleConfiguration;
import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Dropwizard bundle that wires the shared matrix executor and JAX-RS resource.
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

        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(executorService)
                        .to(ExecutorService.class)
                        .named(MatrixResourceBindings.EXECUTOR_BINDING);
                bind(metrics).to(MetricRegistry.class);
            }
        });

        environment.jersey().register(MatrixResource.class);
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
