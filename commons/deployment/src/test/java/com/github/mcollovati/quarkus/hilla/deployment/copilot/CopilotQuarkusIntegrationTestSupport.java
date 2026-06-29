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
package com.github.mcollovati.quarkus.hilla.deployment.copilot;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.server.VaadinContext;
import dev.codex.quarkushilla.copilot.app.CopilotTestBeans;
import dev.codex.quarkushilla.copilot.dependency.DependencyTestBeans;
import dev.codex.quarkushilla.copilot.included.IncludedTestBeans;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import com.github.mcollovati.quarkus.hilla.CopilotQuarkusIntegration;

public final class CopilotQuarkusIntegrationTestSupport {

    private static final String REMOVED_OPTIONAL_ARTIFACTS = String.join(
            ",",
            "io.quarkus:quarkus-hibernate-orm",
            "io.quarkus:quarkus-hibernate-orm-deployment",
            "io.quarkus:quarkus-hibernate-orm-panache-common",
            "io.quarkus:quarkus-hibernate-orm-panache-common-deployment",
            "io.quarkus:quarkus-hibernate-orm-panache",
            "io.quarkus:quarkus-hibernate-orm-panache-deployment",
            "io.quarkus:quarkus-spring-di",
            "io.quarkus:quarkus-spring-di-deployment",
            "io.quarkus:quarkus-spring-data-jpa",
            "io.quarkus:quarkus-spring-data-jpa-deployment");

    private CopilotQuarkusIntegrationTestSupport() {}

    public static QuarkusExtensionTest extensionTest() {
        return new QuarkusExtensionTest()
                .setForcedDependencies(
                        List.of(Dependency.of("com.vaadin", "copilot", System.getProperty("vaadin.version"))))
                .overrideConfigKey("quarkus.class-loading.removed-artifacts", REMOVED_OPTIONAL_ARTIFACTS);
    }

    public static JavaArchive rootArchive() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(
                        CopilotQuarkusIntegrationTestSupport.class,
                        CopilotTestBeans.class,
                        CopilotTestBeans.AAlphabeticallyFirstHelper.class,
                        CopilotTestBeans.ZzzAppShell.class,
                        CopilotTestBeans.ApplicationScopedFlowService.class,
                        CopilotTestBeans.SingletonFlowService.class,
                        CopilotTestBeans.DependentFlowService.class,
                        CopilotTestBeans.RequestScopedFlowService.class,
                        CopilotTestBeans.VaadinServiceScopedFlowService.class,
                        CopilotTestBeans.VaadinSessionScopedFlowService.class,
                        CopilotTestBeans.VaadinUiScopedFlowService.class,
                        CopilotTestBeans.VaadinRouteScopedFlowService.class,
                        CopilotTestBeans.BrowserCallableEndpoint.class,
                        CopilotTestBeans.LegacyEndpoint.class,
                        IncludedTestBeans.class,
                        IncludedTestBeans.IncludedPackageService.class,
                        IncludedTestBeans.ExcludedIncludedService.class);
    }

    public static JavaArchive dependencyArchive() {
        return ShrinkWrap.create(JavaArchive.class, "copilot-flow-services-dependency.jar")
                .addClasses(DependencyTestBeans.class, DependencyTestBeans.DependencyApplicationScopedService.class);
    }

    public static Set<String> flowServiceMethods() {
        return methodIds(CopilotQuarkusIntegration.getFlowUIServices(null));
    }

    public static Set<String> endpointMethods() {
        return methodIds(CopilotQuarkusIntegration.getEndpoints(null));
    }

    public static boolean springBridgeAvailable() {
        try {
            Class<?> springBridge = Class.forName("com.vaadin.copilot.SpringBridge");
            return (boolean) springBridge
                    .getMethod("isSpringAvailable", VaadinContext.class)
                    .invoke(null, new Object[] {null});
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Invalid SpringBridge integration", e);
        }
    }

    public static Set<String> springBridgeFlowServiceMethods() {
        try {
            Class<?> springBridge = Class.forName("com.vaadin.copilot.SpringBridge");
            List<?> services = (List<?>) springBridge
                    .getMethod("getFlowUIServices", VaadinContext.class)
                    .invoke(null, new Object[] {null});
            return methodIds(services);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Invalid SpringBridge integration", e);
        }
    }

    public static String methodId(Class<?> serviceClass, String methodName) {
        return serviceClass.getName() + "#" + methodName;
    }

    private static Set<String> methodIds(Iterable<?> serviceMethodInfos) {
        return java.util.stream.StreamSupport.stream(serviceMethodInfos.spliterator(), false)
                .map(CopilotQuarkusIntegrationTestSupport::methodId)
                .collect(Collectors.toSet());
    }

    private static String methodId(Object serviceMethodInfo) {
        try {
            Class<?> serviceClass = (Class<?>)
                    serviceMethodInfo.getClass().getMethod("serviceClass").invoke(serviceMethodInfo);
            Method serviceMethod = (Method)
                    serviceMethodInfo.getClass().getMethod("serviceMethod").invoke(serviceMethodInfo);
            return methodId(serviceClass, serviceMethod.getName());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Invalid ServiceMethodInfo", e);
        }
    }
}
