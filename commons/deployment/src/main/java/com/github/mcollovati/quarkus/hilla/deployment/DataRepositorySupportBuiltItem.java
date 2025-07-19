/*
 * Copyright 2025 Marco Collovati, Dario Götze
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

import java.util.Collection;
import java.util.EnumSet;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * A build item that tracks supported data repository providers for the application.
 * <p>
 * This class extends {@link SimpleBuildItem} and maintains a collection of enabled
 * data repository providers that will be available during application runtime.
 * <p>
 * The collection of providers is represented as an {@link EnumSet} of {@link Provider}
 * enum values, allowing efficient storage and operations.
 */
final class DataRepositorySupportBuiltItem extends SimpleBuildItem {
    private final EnumSet<Provider> providers;

    /**
     * Creates a new instance with no enabled providers.
     */
    public DataRepositorySupportBuiltItem() {
        this(EnumSet.noneOf(Provider.class));
    }

    /**
     * Creates a new instance with the specified collection of providers.
     *
     * @param providers a collection of providers to be enabled in this build item
     */
    DataRepositorySupportBuiltItem(Collection<Provider> providers) {
        this.providers = EnumSet.copyOf(providers);
    }

    /**
     * Adds a provider to the collection of supported providers.
     *
     * @param provider the provider to add
     */
    void addProvider(Provider provider) {
        providers.add(provider);
    }

    /**
     * Enumeration of supported data repository provider types.
     * <p>
     * Each enum constant represents a specific data repository implementation
     * that can be enabled during the application build process.
     */
    enum Provider {
        /**
         * Spring Data repository implementation
         */
        SPRING_DATA,

        /**
         * Quarkus Panache repository implementation
         */
        PANACHE
    }

    /**
     * Checks if the specified provider is enabled in this build item.
     *
     * @param provider the provider to check
     * @return {@code true} if the provider is enabled, {@code false} otherwise
     */
    boolean isPresent(Provider provider) {
        return providers.contains(provider);
    }
}
