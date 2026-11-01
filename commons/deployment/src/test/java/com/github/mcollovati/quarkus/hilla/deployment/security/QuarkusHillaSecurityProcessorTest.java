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

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.github.mcollovati.quarkus.hilla.deployment.security.HillaSecurityBuildItem.SecurityModel;
import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlProducer;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusHillaSecurityProcessorTest {

    private final QuarkusHillaSecurityProcessor processor = new QuarkusHillaSecurityProcessor();

    @Test
    void formAuthentication_takesPrecedence() {
        HillaSecurityBuildItem item = processor.hillaSecurity(
                new AuthFormBuildItem(true),
                List.of(SecurityInformationBuildItem.OPENIDCONNECT("quarkus.oidc.auth-server-url")));

        assertThat(item.getSecurityModel()).isEqualTo(SecurityModel.FORM);
        assertThat(item.isFormAuthentication()).isTrue();
    }

    @Test
    void basicAuthentication_detectedFromSecurityInformation() {
        HillaSecurityBuildItem item =
                processor.hillaSecurity(new AuthFormBuildItem(false), List.of(SecurityInformationBuildItem.BASIC()));

        assertThat(item.getSecurityModel()).isEqualTo(SecurityModel.BASIC);
        assertThat(item.isEnabled()).isTrue();
        assertThat(item.isFormAuthentication()).isFalse();
    }

    @Test
    void oidcAuthentication_detectedFromSecurityInformation() {
        HillaSecurityBuildItem item = processor.hillaSecurity(
                new AuthFormBuildItem(false),
                List.of(
                        SecurityInformationBuildItem.BASIC(),
                        SecurityInformationBuildItem.OPENIDCONNECT("quarkus.oidc.auth-server-url")));

        assertThat(item.getSecurityModel()).isEqualTo(SecurityModel.OIDC);
    }

    @Test
    void jwtAuthentication_detectedFromSecurityInformation() {
        HillaSecurityBuildItem item =
                processor.hillaSecurity(new AuthFormBuildItem(false), List.of(SecurityInformationBuildItem.JWT()));

        assertThat(item.getSecurityModel()).isEqualTo(SecurityModel.JWT);
    }

    @Test
    void unsupportedOrMissingMechanism_integrationNotActive() {
        assertThat(processor
                        .hillaSecurity(new AuthFormBuildItem(false), List.of(SecurityInformationBuildItem.OAUTH2()))
                        .isEnabled())
                .isFalse();
        assertThat(processor
                        .hillaSecurity(new AuthFormBuildItem(false), List.of())
                        .isEnabled())
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SecurityModel.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void activeSecurityModel_registersAnnotatedCheckerForDefaultDeny(SecurityModel securityModel) {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();

        processor.registerNavigationAccessControl(
                new HillaSecurityBuildItem(securityModel), beans::add, accessCheckers::add);

        assertThat(accessCheckers)
                .extracting(NavigationAccessCheckerBuildItem::getAccessChecker)
                .containsExactly(DotName.createSimple(AnnotatedViewAccessChecker.class));
        assertThat(beans)
                .flatExtracting(AdditionalBeanBuildItem::getBeanClasses)
                .contains(NavigationAccessControlProducer.class.getName());
    }

    @Test
    void noSecurityModel_registersNothing() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();

        processor.registerNavigationAccessControl(
                new HillaSecurityBuildItem(SecurityModel.NONE), beans::add, accessCheckers::add);

        assertThat(accessCheckers).isEmpty();
        assertThat(beans).isEmpty();
    }
}
