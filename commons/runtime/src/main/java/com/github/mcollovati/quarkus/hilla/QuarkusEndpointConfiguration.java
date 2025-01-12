/*
 * Copyright 2023 Marco Collovati, Dario Götze
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

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import static com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration.CONFIG_PREFIX;

/**
 * hilla conf
 */
@ConfigMapping(prefix = CONFIG_PREFIX)
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuarkusEndpointConfiguration {

    String CONFIG_PREFIX = "vaadin.endpoint";
    String VAADIN_ENDPOINT_PREFIX = CONFIG_PREFIX + ".prefix";

    /**
     * prefix
     */
    @WithName("prefix")
    @WithDefault("/connect")
    String getEndpointPrefix();
}
