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
package com.github.mcollovati.quarkus.hilla.build;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.server.frontend.Options;
import com.vaadin.flow.server.frontend.TypeScriptBootstrapModifier;
import com.vaadin.flow.server.frontend.scanner.ClassFinder;
import com.vaadin.flow.server.frontend.scanner.FrontendDependenciesScanner;

/**
 * Workaround to path TransferTypesPlugin to add Mutiny support.
 */
public class TransferTypesPluginPatch implements TypeScriptBootstrapModifier {
    @Override
    public void modify(
            List<String> bootstrapTypeScript,
            Options options,
            FrontendDependenciesScanner frontendDependenciesScanner) {
        addMutinySupport(options.getClassFinder());
    }

    /**
     * Used at build time (quarkus or maven) to add type mappings to the {@code TransferTypesPlugin}
     */
    @SuppressWarnings("unchecked")
    public static void addMutinySupport(ClassFinder classFinder) {
        try {
            Class<?> endpointSubscription =
                    classFinder.loadClass("com.vaadin.hilla.runtime.transfertypes.EndpointSubscription");
            Class<?> typeTransferTypesPluginClass =
                    classFinder.loadClass("com.vaadin.hilla.parser.plugins.transfertypes.TransferTypesPlugin");
            Field field = typeTransferTypesPluginClass.getDeclaredField("classMap");
            field.setAccessible(true);
            Map<String, Class<?>> classMap = (Map<String, Class<?>>) field.get(0);
            if (!classMap.containsKey("io.smallrye.mutiny.Multi")) {
                classMap.put("io.smallrye.mutiny.Multi", endpointSubscription);
                classMap.put("com.github.mcollovati.quarkus.hilla.MutinyEndpointSubscription", endpointSubscription);
            }
        } catch (Exception ex) {
            throw new RuntimeException(
                    "Cannot register additional type mapping in com.vaadin.hilla.parser.plugins.transfertypes.TransferTypesPlugin",
                    ex);
        }
    }
}
