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
import java.util.List;
import java.util.Set;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.vertx.http.runtime.AuthConfig;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import org.jboss.jandex.IndexView;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusHillaSecurityProcessorTest {

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
                        new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.OIDC),
                        new CombinedIndexBuildItem(IndexView.empty(), IndexView.empty()),
                        accessCheckers::add);

        assertThat(accessCheckers.stream().map(item -> item.getAccessChecker().toString()))
                .contains(QuarkusHttpPermissionNavigationAccessChecker.class.getName())
                .doesNotContain(AnnotatedViewAccessChecker.class.getName());
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
