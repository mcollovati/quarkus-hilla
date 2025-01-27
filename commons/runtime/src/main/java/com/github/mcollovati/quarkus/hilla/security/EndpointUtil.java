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
package com.github.mcollovati.quarkus.hilla.security;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;

import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.auth.EndpointAccessChecker;
import io.vertx.ext.web.RoutingContext;
import org.springframework.util.ClassUtils;

import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;

public class EndpointUtil {

    private final EndpointRegistry registry;
    private final EndpointAccessChecker accessChecker;
    private final QuarkusEndpointConfiguration endpointProperties;

    public EndpointUtil(
            EndpointRegistry registry,
            EndpointAccessChecker accessChecker,
            QuarkusEndpointConfiguration endpointProperties) {
        this.registry = registry;
        this.accessChecker = accessChecker;
        this.endpointProperties = endpointProperties;
    }

    public boolean isAnonymousEndpoint(RoutingContext request) {
        var endpointData = getEndpointData(request);
        if (endpointData.isEmpty()) {
            return false;
        }
        var invokedEndpointClass = ClassUtils.getUserClass(endpointData.get().endpointObject());
        var methodDeclaringClass = endpointData.get().method().getDeclaringClass();
        if (methodDeclaringClass.equals(invokedEndpointClass)) {
            return accessChecker
                    .getAccessAnnotationChecker()
                    .hasAccess(endpointData.get().method(), null, role -> false);
        } else {
            return accessChecker.getAccessAnnotationChecker().hasAccess(invokedEndpointClass, null, role -> false);
        }
    }

    private Optional<EndpointData> getEndpointData(RoutingContext context) {
        String endpointPrefix = endpointProperties.getNormalizedEndpointPrefix();
        String requestPath = context.normalizedPath();
        if (!requestPath.startsWith(endpointPrefix)) {
            return Optional.empty();
        }

        String endpointName = context.pathParam("endpoint");
        String methodName = context.pathParam("method");
        if (endpointName == null || methodName == null) {
            return Optional.empty();
        }
        EndpointRegistry.VaadinEndpointData data =
                registry.getEndpoints().get(endpointName.toLowerCase(Locale.ENGLISH));
        if (data == null) {
            return Optional.empty();
        }
        Optional<Method> endpointMethod = data.getMethod(methodName);
        return endpointMethod.map(method -> new EndpointData(method, data.getEndpointObject()));
    }

    private record EndpointData(Method method, Object endpointObject) {}
}
