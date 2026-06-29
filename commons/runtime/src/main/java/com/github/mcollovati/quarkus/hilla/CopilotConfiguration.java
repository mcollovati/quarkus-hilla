/*
 * Copyright 2026 Marco Collovati, Dario Götze
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

import java.util.Optional;
import java.util.Set;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Copilot Flow service discovery configuration.
 */
@ConfigMapping(prefix = "vaadin.copilot.flow-services")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface CopilotConfiguration {

    /**
     * Automatic discovery mode.
     *
     * @return discovery mode.
     */
    @WithDefault("services")
    Discovery discovery();

    /**
     * Package discovery mode.
     *
     * @return package mode.
     */
    @WithName("packages")
    @WithDefault("application")
    PackageMode packageMode();

    /**
     * Bean scope keys included in service discovery mode.
     *
     * @return included scope keys.
     */
    @WithName("include-scopes")
    @WithDefault("application,singleton,dependent,vaadin-service,vaadin-session,vaadin-ui,vaadin-route")
    Set<String> includeScopes();

    /**
     * Package prefixes that are always added to discovery.
     *
     * @return included package prefixes.
     */
    @WithName("include-packages")
    Optional<Set<String>> includePackages();

    /**
     * Package prefixes that are always removed from discovery.
     *
     * @return excluded package prefixes.
     */
    @WithName("exclude-packages")
    Optional<Set<String>> excludePackages();

    /**
     * Classes that are always added to discovery.
     *
     * @return included classes.
     */
    @WithName("include-classes")
    Optional<Set<String>> includeClasses();

    /**
     * Classes that are always removed from discovery.
     *
     * @return excluded classes.
     */
    @WithName("exclude-classes")
    Optional<Set<String>> excludeClasses();

    enum Discovery {
        NONE,
        SERVICES,
        ALL
    }

    enum PackageMode {
        APPLICATION,
        ALL
    }
}
