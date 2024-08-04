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

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "vaadin")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuarkusHillaConfigurationProperties {

    /**
     * prefix
     */
    @WithName("exposeServerRoutesToClient")
    @WithDefault("true")
    boolean exposeServerRoutesToClient();

    /**
     * Any
     * @return
     */
    @WithUnnamedKey("project")
    Map<String, String> project();
    /**
     * Any
     * @return
     */
    @WithUnnamedKey("frontend")
    Map<String, String> frontend();
}