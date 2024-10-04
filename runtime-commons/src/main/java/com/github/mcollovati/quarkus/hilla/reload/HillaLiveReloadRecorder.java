/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.reload;

import java.io.IOException;
import java.util.Set;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.runtime.annotations.Recorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mcollovati.quarkus.hilla.HillaConfiguration;

@Recorder
public class HillaLiveReloadRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(HillaLiveReloadRecorder.class);

    private static volatile HotReplacementContext hotReplacementContext;
    private static AbstractEndpointsWatcher endpointSourcesWatcher;

    public static void setHotReplacement(HotReplacementContext context) {
        hotReplacementContext = context;
    }

    public void startEndpointWatcher(boolean liveReload, HillaConfiguration configuration) {
        LOGGER.debug("{}tarting endpoint live reload watcher", liveReload ? "Re" : "S");
        if (liveReload) {
            stopEndpointWatcher();
        }

        try {
            if (configuration.liveReload().watchStrategy()
                    == HillaConfiguration.LiveReloadConfig.WatchStrategy.SOURCE) {
                endpointSourcesWatcher = new EndpointSourcesWatcher(
                        hotReplacementContext,
                        configuration.liveReload().watchedPaths().orElse(Set.of()));
            } else {
                endpointSourcesWatcher = new EndpointClassesWatcher(
                        hotReplacementContext,
                        configuration.liveReload().watchedPaths().orElse(Set.of()));
            }
            new Thread(endpointSourcesWatcher).start();
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cannot start endpoint watcher", e);
            } else {
                LOGGER.warn("Cannot start endpoint watcher: {}", e.getMessage());
            }
        }
    }

    public static void stopEndpointWatcher() {
        if (endpointSourcesWatcher != null) {
            endpointSourcesWatcher.stop();
        }
        endpointSourcesWatcher = null;
    }

    public static void closeEndpointWatcher() {
        stopEndpointWatcher();
        hotReplacementContext = null;
    }
}
