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
 * The configuration for the Vaadin endpoint.
 */
@ConfigMapping(prefix = CONFIG_PREFIX)
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuarkusEndpointConfiguration {

    String CONFIG_PREFIX = "vaadin.endpoint";
    String VAADIN_ENDPOINT_PREFIX = CONFIG_PREFIX + ".prefix";
    String DEFAULT_ENDPOINT_PREFIX = "/connect";

    /**
     * The prefix for the Vaadin endpoint.
     * @return the connect endpoint prefix, default is "/connect"
     */
    @WithName("prefix")
    @WithDefault(DEFAULT_ENDPOINT_PREFIX)
    String getEndpointPrefix();

    /**
     * It is the same as {@link #getEndpointPrefix()} but ensures a starting slash and removes a trailing slash.
     * @return the trimmed endpoint prefix, default is "/connect"
     */
    default String getNormalizedEndpointPrefix() {
        String prefix = getEndpointPrefix();
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    /**
     * Checks if the endpoint prefix is the default one.
     * @return true if the endpoint prefix is the default one
     */
    default boolean isDefaultEndpointPrefix() {
        return DEFAULT_ENDPOINT_PREFIX.equals(getNormalizedEndpointPrefix());
    }
}
