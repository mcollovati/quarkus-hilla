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

import io.quarkus.builder.item.MultiBuildItem;
import org.jboss.jandex.DotName;

/**
 * This build item is used to specify one or more additional bean classes that should be enhanced with
 * {@code FilterableRepository} implementation.
 */
public final class FilterableRepositoryImplementorBuildItem extends MultiBuildItem {

    private final DotName filterableInterface;
    private final DotName implementor;

    public FilterableRepositoryImplementorBuildItem(DotName filterableInterface, DotName implementor) {
        this.filterableInterface = filterableInterface;
        this.implementor = implementor;
    }

    public DotName getFilterableInterface() {
        return filterableInterface;
    }

    public DotName getImplementor() {
        return implementor;
    }
}
