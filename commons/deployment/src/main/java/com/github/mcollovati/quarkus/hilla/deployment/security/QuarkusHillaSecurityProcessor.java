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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import com.github.mcollovati.quarkus.hilla.security.EndpointUtil;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.security.QuarkusAccessPathChecker;
import com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

class QuarkusHillaSecurityProcessor {

    @BuildStep
    HillaSecurityBuildItem hillaSecurityBuildItem(
            Capabilities capabilities,
            List<SecurityInformationBuildItem> securityInformation,
            VertxHttpBuildTimeConfig httpBuildTimeConfig) {
        if (httpBuildTimeConfig.auth().form()) {
            return new HillaSecurityBuildItem(HillaSecurityBuildItem.SecurityModel.FORM);
        }

        HillaSecurityBuildItem.SecurityModel securityModel = securityInformation.stream()
                .map(QuarkusHillaSecurityProcessor::toSecurityModel)
                .findFirst()
                .orElseGet(() -> detectSecurityModelFromCapabilities(capabilities));
        return new HillaSecurityBuildItem(securityModel);
    }

    @BuildStep
    void registerHillaSecurityPolicy(
            HillaSecurityBuildItem hillaSecurity, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (hillaSecurity.isAuthEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(HillaSecurityPolicy.class, EndpointUtil.class, QuarkusAccessPathChecker.class)
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
        if (hillaSecurity.isFormAuthEnabled()) {
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
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        if (hillaSecurity.isFormAuthEnabled()) {
            recorder.configureFormLoginHttpSecurityPolicy(beanContainer.getValue());
        }
        if (hillaSecurity.isAuthEnabled()) {
            recorder.markSecurityPolicyUsed();
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
        if (accessCheckers.isEmpty()) {
            return;
        }
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(accessCheckers.stream()
                        .map(item -> item.getAccessChecker().toString())
                        .toList())
                .setUnremovable()
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .build());
    }

    @BuildStep
    void registerHttpPermissionNavigationReflection(
            HillaSecurityBuildItem hillaSecurity, BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        if (hillaSecurity.isAuthEnabled()) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                            "io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy",
                            "io.quarkus.vertx.http.runtime.security.AbstractPathMatchingHttpSecurityPolicy$HttpMatcher",
                            "io.quarkus.vertx.http.runtime.security.HttpSecurityConfiguration",
                            "io.quarkus.vertx.http.runtime.security.RolesAllowedHttpSecurityPolicy",
                            "io.quarkus.vertx.http.runtime.security.RolesMapping")
                    .fields()
                    .build());
        }
    }

    @BuildStep
    void registerNavigationAccessControl(
            HillaSecurityBuildItem hillaSecurity,
            CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<NavigationAccessControlBuildItem> accessControlProducer,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (hillaSecurity.isAuthEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            QuarkusNavigationAccessControl.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            DefaultAccessCheckDecisionResolver.class)
                    .setUnremovable()
                    .build());
            registerNavigationAccessCheckers(hillaSecurity, index, accessCheckerProducer);

            navigationLoginPath(hillaSecurity)
                    .map(NavigationAccessControlBuildItem::new)
                    .ifPresent(accessControlProducer::produce);
        }
    }

    void registerNavigationAccessCheckers(
            HillaSecurityBuildItem hillaSecurity,
            CombinedIndexBuildItem index,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (!hillaSecurity.isAuthEnabled()) {
            return;
        }
        if (hasSecuredRoutes(index)) {
            accessCheckerProducer.produce(
                    new NavigationAccessCheckerBuildItem(DotName.createSimple(AnnotatedViewAccessChecker.class)));
        }
        accessCheckerProducer.produce(new NavigationAccessCheckerBuildItem(
                DotName.createSimple(QuarkusHttpPermissionNavigationAccessChecker.class)));
    }

    private static HillaSecurityBuildItem.SecurityModel toSecurityModel(
            SecurityInformationBuildItem securityInformation) {
        return switch (securityInformation.getSecurityModel()) {
            case basic -> HillaSecurityBuildItem.SecurityModel.BASIC;
            case jwt -> HillaSecurityBuildItem.SecurityModel.JWT;
            case oauth2 -> HillaSecurityBuildItem.SecurityModel.OAUTH2;
            case oidc -> HillaSecurityBuildItem.SecurityModel.OIDC;
        };
    }

    private static HillaSecurityBuildItem.SecurityModel detectSecurityModelFromCapabilities(Capabilities capabilities) {
        if (capabilities.isPresent(Capability.OIDC)) {
            return HillaSecurityBuildItem.SecurityModel.OIDC;
        }
        if (capabilities.isPresent(Capability.JWT)) {
            return HillaSecurityBuildItem.SecurityModel.JWT;
        }
        if (capabilities.isPresent(Capability.SECURITY_ELYTRON_OAUTH2)) {
            return HillaSecurityBuildItem.SecurityModel.OAUTH2;
        }
        if (capabilities.isPresent(Capability.SECURITY_JPA)) {
            return HillaSecurityBuildItem.SecurityModel.JPA;
        }
        if (capabilities.isPresent(Capability.SECURITY_ELYTRON_JDBC)) {
            return HillaSecurityBuildItem.SecurityModel.JDBC;
        }
        if (capabilities.isPresent(Capability.SECURITY_ELYTRON_LDAP)) {
            return HillaSecurityBuildItem.SecurityModel.LDAP;
        }
        if (capabilities.isPresent(Capability.SECURITY)) {
            return HillaSecurityBuildItem.SecurityModel.SECURITY_EXTENSION;
        }
        return HillaSecurityBuildItem.SecurityModel.NONE;
    }

    private Optional<String> navigationLoginPath(HillaSecurityBuildItem hillaSecurity) {
        if (hillaSecurity.isFormAuthEnabled()) {
            return ConfigProvider.getConfig().getOptionalValue("quarkus.http.auth.form.login-page", String.class);
        }
        return ConfigProvider.getConfig().getOptionalValue("vaadin.security.login-path", String.class);
    }

    private boolean hasSecuredRoutes(CombinedIndexBuildItem indexBuildItem) {
        Set<DotName> securityAnnotations = Set.of(
                DotName.createSimple(DenyAll.class.getName()),
                DotName.createSimple(AnonymousAllowed.class.getName()),
                DotName.createSimple(RolesAllowed.class.getName()),
                DotName.createSimple(PermitAll.class.getName()));
        return indexBuildItem.getComputingIndex().getAnnotations(DotName.createSimple(Route.class.getName())).stream()
                .flatMap(route -> route.target().annotations().stream().map(AnnotationInstance::name))
                .anyMatch(securityAnnotations::contains);
    }
}
