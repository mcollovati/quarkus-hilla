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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import com.vaadin.quarkus.deployment.vaadinplugin.VaadinBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.DotName;

import com.github.mcollovati.quarkus.hilla.security.EndpointUtil;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

class QuarkusHillaSecurityProcessor {

    @BuildStep
    FileRoutesManifestBuildItem fileRoutesManifestExpected(
            HillaSecurityBuildItem hillaSecurity,
            VaadinBuildTimeConfig vaadinConfig,
            CurateOutcomeBuildItem curateOutcome) {
        if (!hillaSecurity.isEnabled()) {
            return new FileRoutesManifestBuildItem(false);
        }
        var applicationModule = curateOutcome.getApplicationModel().getApplicationModule();
        if (applicationModule == null) {
            // Vaadin can recover workspace information from its generated
            // metadata. If it is unavailable here, classification is not
            // possible, so keep route security fail-closed.
            return new FileRoutesManifestBuildItem(true);
        }
        File configuredFrontend = vaadinConfig.frontendDirectory();
        File frontendDirectory =
                resolveFrontendDirectory(applicationModule.getModuleDir().toPath(), configuredFrontend);
        return new FileRoutesManifestBuildItem(
                FileRoutesManifestClassifier.isManifestExpected(frontendDirectory, vaadinConfig.reactEnabled()));
    }

    static File resolveFrontendDirectory(Path moduleDirectory, File configuredFrontend) {
        Path configuredPath = configuredFrontend.toPath();
        Path frontendDirectory = configuredPath.isAbsolute() ? configuredPath : moduleDirectory.resolve(configuredPath);
        if (configuredPath.equals(Path.of("src", "main", "frontend")) && !Files.isDirectory(frontendDirectory)) {
            Path legacyFrontendDirectory = moduleDirectory.resolve("frontend");
            if (Files.isDirectory(legacyFrontendDirectory)) {
                return legacyFrontendDirectory.toFile();
            }
        }
        return frontendDirectory.toFile();
    }

    @BuildStep
    HillaSecurityBuildItem hillaSecurityBuildItem(List<SecurityInformationBuildItem> securityInformation) {
        boolean authFormEnabled = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.auth.form.enabled", Boolean.class)
                .orElse(false);
        if (authFormEnabled) {
            return new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.FORM);
        }
        HillaSecurityBuildItem.SecurityModel securityModel = securityInformation.stream()
                .map(QuarkusHillaSecurityProcessor::toSecurityModel)
                .filter(model -> model != HillaSecurityBuildItem.SecurityModel.NONE)
                .findFirst()
                .orElse(HillaSecurityBuildItem.SecurityModel.NONE);
        return new HillaSecurityBuildItem(securityModel);
    }

    @BuildStep
    void registerHillaSecurityPolicy(
            HillaSecurityBuildItem hillaSecurity, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (hillaSecurity.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(HillaSecurityPolicy.class, EndpointUtil.class)
                    .setDefaultScope(DotNames.APPLICATION_SCOPED)
                    .setUnremovable()
                    .build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHillaFormAuthenticationMechanism(
            HillaSecurityBuildItem hillaSecurity,
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        if (hillaSecurity.isFormAuthentication()) {
            producer.produce(SyntheticBeanBuildItem.configure(HillaFormAuthenticationMechanism.class)
                    .types(HttpAuthenticationMechanism.class)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .alternative(true)
                    .priority(1)
                    .supplier(recorder.setupFormAuthenticationMechanism())
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void configureHillaSecurityComponents(
            HillaSecurityBuildItem hillaSecurity,
            FileRoutesManifestBuildItem fileRoutesManifest,
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        if (hillaSecurity.isEnabled()) {
            recorder.configureHttpSecurityPolicy(
                    beanContainer.getValue(), fileRoutesManifest.isExpected(), hillaSecurity.isFormAuthentication());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureNavigationAccessControl(
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer,
            Optional<NavigationAccessControlBuildItem> navigationAccessControlBuildItem) {
        navigationAccessControlBuildItem
                .map(NavigationAccessControlBuildItem::getLoginPath)
                .ifPresent(loginPath -> recorder.configureNavigationAccessControl(beanContainer.getValue(), loginPath));
    }

    @BuildStep
    void configureNavigationControlAccessCheckers(
            List<NavigationAccessCheckerBuildItem> accessCheckers, BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(accessCheckers.stream()
                        .map(item -> item.getAccessChecker().toString())
                        .toList())
                .setUnremovable()
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .build());
    }

    @BuildStep
    void registerNavigationAccessControl(
            HillaSecurityBuildItem hillaSecurity,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<NavigationAccessControlBuildItem> accessControlProducer,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (hillaSecurity.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            QuarkusNavigationAccessControl.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            DefaultAccessCheckDecisionResolver.class)
                    .setUnremovable()
                    .build());
            accessCheckerProducer.produce(
                    new NavigationAccessCheckerBuildItem(DotName.createSimple(AnnotatedViewAccessChecker.class)));

            if (hillaSecurity.isFormAuthentication()) {
                ConfigProvider.getConfig()
                        .getOptionalValue("quarkus.http.auth.form.login-page", String.class)
                        .map(NavigationAccessControlBuildItem::new)
                        .ifPresent(accessControlProducer::produce);
            }
        }
    }

    private static HillaSecurityBuildItem.SecurityModel toSecurityModel(
            SecurityInformationBuildItem securityInformation) {
        return switch (securityInformation.getSecurityModel()) {
            case basic -> HillaSecurityBuildItem.SecurityModel.BASIC;
            case oidc -> HillaSecurityBuildItem.SecurityModel.OIDC;
            case jwt -> HillaSecurityBuildItem.SecurityModel.JWT;
            case oauth2 -> HillaSecurityBuildItem.SecurityModel.NONE;
        };
    }
}
