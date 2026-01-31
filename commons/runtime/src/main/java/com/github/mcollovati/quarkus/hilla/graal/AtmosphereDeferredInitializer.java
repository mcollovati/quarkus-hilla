/*
 * Copyright 2000-2024 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.graal;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

import org.atmosphere.cpr.AtmosphereFramework;

/**
 * Deferred initializer for Atmosphere framework in GraalVM native images.
 * <p>
 * This class handles Atmosphere initialization that needs to be deferred until runtime
 * in native image builds to prevent issues with thread startup during build time.
 * </p>
 */
public class AtmosphereDeferredInitializer {

    transient List<AtmosphereFramework> frameworks = new ArrayList<>();

    /*
     * Called by recorder at RUNTIME_INIT to complete deferred Atmosphere
     * initialization that cannot be performed at build time, e.g. starting
     * thread pools.
     */
    static void completeInitialization(ServletContext servletContext) {
        AtmosphereDeferredInitializer initializer = getOrCreateInitializer(servletContext);
        initializer.frameworks.forEach(DelayedInitBroadcaster::startExecutors);
    }

    /**
     * Takes a reference to an Atmosphere instance to defer the initialization
     * at RUNTIME_INIT phase, performed by a Recorder.
     *
     * @param config the servlet configuration
     * @param framework the Atmosphere framework to register
     */
    public static void register(ServletConfig config, AtmosphereFramework framework) {
        ServletContext context = config.getServletContext();
        AtmosphereDeferredInitializer initializer = getOrCreateInitializer(context);
        initializer.frameworks.add(framework);
    }

    private static AtmosphereDeferredInitializer getOrCreateInitializer(ServletContext context) {
        AtmosphereDeferredInitializer initializer =
                (AtmosphereDeferredInitializer) context.getAttribute(AtmosphereDeferredInitializer.class.getName());
        if (initializer == null) {
            initializer = new AtmosphereDeferredInitializer();
            context.setAttribute(AtmosphereDeferredInitializer.class.getName(), initializer);
        }
        return initializer;
    }
}
