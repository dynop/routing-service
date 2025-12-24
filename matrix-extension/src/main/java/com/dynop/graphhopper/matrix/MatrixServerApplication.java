package com.dynop.graphhopper.matrix;

import com.dynop.graphhopper.matrix.config.MatrixBundle;
import com.graphhopper.application.cli.ImportCommand;
import com.graphhopper.application.cli.MatchCommand;
import com.graphhopper.application.resources.RootResource;
import com.graphhopper.http.CORSFilter;
import com.graphhopper.http.GraphHopperBundle;
import com.graphhopper.navigation.NavigateResource;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;

import jakarta.servlet.DispatcherType;
import java.util.EnumSet;

/**
 * Dropwizard application that bootstraps GraphHopper along with the custom MatrixBundle.
 */
public final class MatrixServerApplication extends Application<MatrixServerConfiguration> {

    public static void main(String[] args) throws Exception {
        new MatrixServerApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<MatrixServerConfiguration> bootstrap) {
        bootstrap.addBundle(new GraphHopperBundle());
        bootstrap.addBundle(new MatrixBundle());
        bootstrap.addCommand(new ImportCommand());
        bootstrap.addCommand(new MatchCommand());
        bootstrap.addBundle(new AssetsBundle("/com/graphhopper/maps/", "/maps/", "index.html"));
        bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars", "/webjars/", null, "webjars"));
    }

    @Override
    public void run(MatrixServerConfiguration configuration, Environment environment) {
        environment.jersey().register(new RootResource());
        environment.jersey().register(NavigateResource.class);
        environment.servlets()
                .addFilter("cors", CORSFilter.class)
                .addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "*");
    }
}
