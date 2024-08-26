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
package com.github.mcollovati.quarkus.hilla;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Hilla configuration.
 */
@ConfigMapping(prefix = "vaadin.hilla")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface HillaConfiguration {

    /**
     * Configuration properties for endpoints live reload.
     *
     * @return configuration properties for endpoints live reload.
     */
    LiveReloadConfig liveReload();

    /**
     * Configuration properties for endpoints hot reload.
     * <p></p>
     * The extension watches source folders for changes in Java files and triggers a live reload if affected classes are Hilla endpoints or used in endpoints.
     */
    interface LiveReloadConfig {

        /**
         * Enabled endpoints live reload.
         * @return {@literal true} if live reload is enabled, otherwise {@literal false}
         */
        @WithDefault("true")
        boolean enable();

        /**
         * The list of paths to watch for changes, relative to a root folder.
         * <p></p>
         * For example, given a SOURCE {@link #watchStrategy()} and Maven project with source code in the default {@literal src/main/java} folder and
         * endpoints related classes in {@literal src/main/java/com/example/service} and {@literal src/main/java/com/example/model},
         * the configuration should be {@literal vaadin.hilla.live-reload.watchedSourcePaths=com/example/service,com/example/model}.
         * <p></p>
         * By default, all sub folders are watched.
         *
         * @return the list of paths to watch for changes.
         */
        Optional<Set<Path>> watchedPaths();

        /**
         * The strategy to use to watch for changes in Hilla endpoints.
         * <p></p>
         * @return the strategy to use to watch for changes in Hilla endpoints.
         */
        @WithDefault("CLASS")
        WatchStrategy watchStrategy();

        /**
         * The strategy to use to watch for changes in Hilla endpoints.
         */
        enum WatchStrategy {
            /**
             * Watch for changes in source files
             */
            SOURCE,
            /**
             * Watch for changes in compiled classes.
             * <p></p>
             * Best to be used in combination with {@literal quarkus.live-reload.instrumentation=true} to prevent excessive server restarts.
             */
            CLASS
        }
    }
}
