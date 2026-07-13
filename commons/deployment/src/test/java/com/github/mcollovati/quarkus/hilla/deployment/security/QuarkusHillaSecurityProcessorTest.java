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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.undertow.deployment.FilterBuildItem;
import io.quarkus.vertx.http.runtime.AuthConfig;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusHillaSecurityProcessorTest {

    private static final String HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker";
    private static final String ANNOTATED_VIEW_ACCESS_CHECKER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusAnnotatedViewAccessChecker";
    private static final String SECURITY_IDENTITY_CAPTURE_FILTER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusSecurityIdentityCaptureFilter";
    private static final String ANNOTATION_CONFIG_MISMATCH_DIAGNOSTICS =
            "com.github.mcollovati.quarkus.hilla.security.AnnotationConfigMismatchDiagnostics";

    @Test
    void hillaSecurityBuildItem_genericSecurityCapability_enablesSecurityExtensionModel() {
        HillaSecurityBuildItem buildItem = new QuarkusHillaSecurityProcessor()
                .hillaSecurityBuildItem(
                        new Capabilities(Set.of(Capability.SECURITY)), List.of(), httpBuildTimeConfig(false));

        assertThat(buildItem.getSecurityModel()).isEqualTo(HillaSecurityBuildItem.SecurityModel.SECURITY_EXTENSION);
    }

    @Test
    void registerNavigationAccessCheckers_withoutSecuredRoutes_registersHttpPermissionChecker() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessCheckers(
                        new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.OIDC), accessCheckers::add);

        assertThat(accessCheckers.stream().map(item -> item.getAccessChecker().toString()))
                .contains(HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER)
                .doesNotContain(ANNOTATED_VIEW_ACCESS_CHECKER);
    }

    @Test
    void registerHillaSecurityPolicy_authEnabledRegistersIdentityCaptureFilter() {
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();
        List<FilterBuildItem> filters = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerHillaSecurityPolicy(
                        new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.OIDC),
                        beans::add,
                        filters::add);

        assertThat(filters).singleElement().satisfies(filter -> {
            assertThat(filter.getFilterClass()).isEqualTo(SECURITY_IDENTITY_CAPTURE_FILTER);
            assertThat(filter.getMappings()).singleElement().satisfies(mapping -> assertThat(mapping.getMapping())
                    .isEqualTo("/*"));
        });
        assertThat(beans).singleElement().satisfies(bean -> assertThat(bean.getBeanClasses())
                .contains(ANNOTATION_CONFIG_MISMATCH_DIAGNOSTICS));
    }

    @Test
    void registerHillaSecurityPolicy_withoutAuthenticationRegistersNothing() {
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();
        List<FilterBuildItem> filters = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerHillaSecurityPolicy(
                        new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.NONE),
                        beans::add,
                        filters::add);

        assertThat(beans).isEmpty();
        assertThat(filters).isEmpty();
    }

    @Test
    void registerAnnotationDiagnostics_withoutAuthenticationRegistersNothing() {
        List<SyntheticBeanBuildItem> configurations = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerAnnotationConfigMismatchConfiguration(
                        new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.NONE),
                        null,
                        configurations::add);

        assertThat(configurations).isEmpty();
    }

    @Test
    void securityProcessor_doesNotRegisterQuarkusHttpSecurityInternalsForReflection() {
        assertThat(Arrays.stream(QuarkusHillaSecurityProcessor.class.getDeclaredMethods())
                        .map(method -> method.getName()))
                .doesNotContain("registerHttpPermissionNavigationReflection");
    }

    private static VertxHttpBuildTimeConfig httpBuildTimeConfig(boolean formAuth) {
        AuthConfig authConfig = (AuthConfig) Proxy.newProxyInstance(
                QuarkusHillaSecurityProcessorTest.class.getClassLoader(),
                new Class<?>[] {AuthConfig.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "form" -> formAuth;
                    case "toString" -> "AuthConfig";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (VertxHttpBuildTimeConfig) Proxy.newProxyInstance(
                QuarkusHillaSecurityProcessorTest.class.getClassLoader(),
                new Class<?>[] {VertxHttpBuildTimeConfig.class},
                (ignored, method, args) -> switch (method.getName()) {
                    case "auth" -> authConfig;
                    case "toString" -> "VertxHttpBuildTimeConfig";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
