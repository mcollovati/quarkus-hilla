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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;
import com.github.mcollovati.quarkus.hilla.security.VaadinSecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusHillaSecurityProcessorTest {

    @Test
    void formAuthentication_registersAnnotatedCheckerForDefaultDeny() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessControl(
                        new AuthFormBuildItem(true),
                        securityConfig(true),
                        beans::add,
                        ignored -> {},
                        accessCheckers::add);

        assertThat(accessCheckers)
                .extracting(NavigationAccessCheckerBuildItem::getAccessChecker)
                .containsExactly(DotName.createSimple(AnnotatedViewAccessChecker.class));
        assertThat(beans)
                .flatExtracting(AdditionalBeanBuildItem::getBeanClasses)
                .contains(QuarkusNavigationAccessControl.class.getName());
    }

    @Test
    void navigationAccessControlDisabled_registersNoCheckerButKeepsAccessControl() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessControl(
                        new AuthFormBuildItem(true),
                        securityConfig(false),
                        beans::add,
                        ignored -> {},
                        accessCheckers::add);

        assertThat(accessCheckers).isEmpty();
        // HillaSecurityPolicy injects NavigationAccessControl, so the bean must
        // stay registered even when access checking is disabled.
        assertThat(beans)
                .flatExtracting(AdditionalBeanBuildItem::getBeanClasses)
                .contains(QuarkusNavigationAccessControl.class.getName());
    }

    @Test
    void formAuthenticationDisabled_registersNoChecker() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessControl(
                        new AuthFormBuildItem(false),
                        securityConfig(true),
                        beans::add,
                        ignored -> {},
                        accessCheckers::add);

        assertThat(accessCheckers).isEmpty();
        assertThat(beans).isEmpty();
    }

    private static VaadinSecurityConfig securityConfig(boolean navigationAccessControlEnabled) {
        return new VaadinSecurityConfig() {

            @Override
            public String logoutPath() {
                return "/logout";
            }

            @Override
            public Optional<String> postLogoutRedirectUri() {
                return Optional.empty();
            }

            @Override
            public boolean logoutInvalidateSession() {
                return true;
            }

            @Override
            public NavigationAccessControlConfig navigationAccessControl() {
                return () -> navigationAccessControlEnabled;
            }
        };
    }
}
