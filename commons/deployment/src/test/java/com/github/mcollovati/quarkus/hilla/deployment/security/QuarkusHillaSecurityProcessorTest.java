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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusHillaSecurityProcessorTest {

    @TempDir
    Path moduleDirectory;

    @Test
    void unavailableApplicationModuleExpectsManifestToFailClosed() {
        var applicationModel = new ApplicationModelBuilder()
                .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                        .setGroupId("org.acme")
                        .setArtifactId("app")
                        .setVersion("1.0"))
                .build();
        CurateOutcomeBuildItem curateOutcome = new CurateOutcomeBuildItem(applicationModel);

        FileRoutesManifestBuildItem result = new QuarkusHillaSecurityProcessor()
                .fileRoutesManifestExpected(security(HillaSecurityBuildItem.SecurityModel.FORM), null, curateOutcome);

        assertThat(result.isExpected()).isTrue();
    }

    @Test
    void disabledFormAuthenticationSkipsManifestClassification() {
        FileRoutesManifestBuildItem result = new QuarkusHillaSecurityProcessor()
                .fileRoutesManifestExpected(security(HillaSecurityBuildItem.SecurityModel.NONE), null, null);

        assertThat(result.isExpected()).isFalse();
    }

    @Test
    void formAuthentication_registersAnnotatedCheckerForDefaultDeny() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessControl(
                        security(HillaSecurityBuildItem.SecurityModel.FORM),
                        ignored -> {},
                        ignored -> {},
                        accessCheckers::add);

        assertThat(accessCheckers)
                .extracting(NavigationAccessCheckerBuildItem::getAccessChecker)
                .containsExactly(DotName.createSimple(AnnotatedViewAccessChecker.class));
    }

    @Test
    void oidcSecurity_registersAnnotatedCheckerForDefaultDeny() {
        List<NavigationAccessCheckerBuildItem> accessCheckers = new ArrayList<>();

        new QuarkusHillaSecurityProcessor()
                .registerNavigationAccessControl(
                        security(HillaSecurityBuildItem.SecurityModel.OIDC),
                        ignored -> {},
                        ignored -> {},
                        accessCheckers::add);

        assertThat(accessCheckers)
                .extracting(NavigationAccessCheckerBuildItem::getAccessChecker)
                .containsExactly(DotName.createSimple(AnnotatedViewAccessChecker.class));
    }

    @Test
    void oidcSecurityInformation_activatesOidcModel() {
        HillaSecurityBuildItem result = new QuarkusHillaSecurityProcessor()
                .hillaSecurityBuildItem(List.of(SecurityInformationBuildItem.OPENIDCONNECT("/q/oidc")));

        assertThat(result.securityModel()).isEqualTo(HillaSecurityBuildItem.SecurityModel.OIDC);
    }

    @Test
    void jwtSecurityInformation_activatesJwtModel() {
        HillaSecurityBuildItem result =
                new QuarkusHillaSecurityProcessor().hillaSecurityBuildItem(List.of(SecurityInformationBuildItem.JWT()));

        assertThat(result.securityModel()).isEqualTo(HillaSecurityBuildItem.SecurityModel.JWT);
    }

    @Test
    void unsupportedSecurityInformation_doesNotActivateHillaSecurity() {
        HillaSecurityBuildItem result = new QuarkusHillaSecurityProcessor()
                .hillaSecurityBuildItem(List.of(SecurityInformationBuildItem.BASIC()));

        assertThat(result.securityModel()).isEqualTo(HillaSecurityBuildItem.SecurityModel.NONE);
    }

    @Test
    void defaultMissingFrontendDirectoryUsesLegacyFrontendDirectory() throws Exception {
        Path legacyFrontend = Files.createDirectory(moduleDirectory.resolve("frontend"));

        File resolved =
                QuarkusHillaSecurityProcessor.resolveFrontendDirectory(moduleDirectory, new File("src/main/frontend"));

        assertThat(resolved.toPath()).isEqualTo(legacyFrontend);
    }

    @Test
    void existingDefaultFrontendDirectoryWinsOverLegacyFrontendDirectory() throws Exception {
        Path defaultFrontend = Files.createDirectories(moduleDirectory.resolve("src/main/frontend"));
        Files.createDirectory(moduleDirectory.resolve("frontend"));

        File resolved =
                QuarkusHillaSecurityProcessor.resolveFrontendDirectory(moduleDirectory, new File("src/main/frontend"));

        assertThat(resolved.toPath()).isEqualTo(defaultFrontend);
    }

    @Test
    void customRelativeFrontendDirectoryRemainsConfigured() throws Exception {
        Files.createDirectory(moduleDirectory.resolve("frontend"));

        File resolved = QuarkusHillaSecurityProcessor.resolveFrontendDirectory(moduleDirectory, new File("web/client"));

        assertThat(resolved.toPath()).isEqualTo(moduleDirectory.resolve("web/client"));
    }

    @Test
    void absoluteFrontendDirectoryRemainsConfigured() throws Exception {
        Files.createDirectory(moduleDirectory.resolve("frontend"));
        Path absoluteFrontend = moduleDirectory.resolve("external-client").toAbsolutePath();

        File resolved =
                QuarkusHillaSecurityProcessor.resolveFrontendDirectory(moduleDirectory, absoluteFrontend.toFile());

        assertThat(resolved.toPath()).isEqualTo(absoluteFrontend);
    }

    private static HillaSecurityBuildItem security(HillaSecurityBuildItem.SecurityModel model) {
        return new HillaSecurityBuildItem(model);
    }
}
