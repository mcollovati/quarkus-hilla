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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.hilla.engine.EngineAutoConfiguration;
import org.springframework.context.ApplicationContext;

public final class HillaReplacements {

    /**
     * Finds all beans in the application context that have a browser callable
     * annotation.
     *
     * @param engineConfiguration the engine configuration that provides the annotations to
     *                            search for
     * @param applicationContext  the application context to search for beans in
     * @return a list of classes that qualify as browser callables
     */
    public static List<Class<?>> findBrowserCallables(
            EngineAutoConfiguration engineConfiguration, ApplicationContext applicationContext) {
        return engineConfiguration.getEndpointAnnotations().stream()
                .map(applicationContext::getBeansWithAnnotation)
                .map(Map::values)
                .flatMap(Collection::stream)
                // maps to original class when proxies are found
                // (also converts to class in all cases)
                .map(SpringReplacements::classUtils_getUserClass)
                .distinct()
                .collect(Collectors.toList());
    }
}
