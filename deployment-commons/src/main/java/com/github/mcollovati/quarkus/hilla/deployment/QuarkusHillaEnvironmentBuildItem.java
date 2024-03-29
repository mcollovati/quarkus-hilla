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
package com.github.mcollovati.quarkus.hilla.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Marker used by Build Steps that should be executed if Hilla extension is running in stand-alone mode,
 * meaning that Vaadin Quarkus extension is not available.
 */
public final class QuarkusHillaEnvironmentBuildItem extends SimpleBuildItem {

    private final boolean hybrid;

    public QuarkusHillaEnvironmentBuildItem(boolean hybrid) {
        this.hybrid = hybrid;
    }

    public boolean isHybrid() {
        return hybrid;
    }
}
