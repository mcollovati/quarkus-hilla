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
import jakarta.inject.Singleton;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Optional;

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
import io.quarkus.undertow.deployment.FilterBuildItem;
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.jboss.jandex.DotName;

import com.github.mcollovati.quarkus.hilla.security.AnnotationConfigMismatchDiagnostics;
import com.github.mcollovati.quarkus.hilla.security.EndpointUtil;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;
import com.github.mcollovati.quarkus.hilla.security.QuarkusSecurityIdentityAugmentor;
import com.github.mcollovati.quarkus.hilla.security.QuarkusSecurityIdentityCaptureFilter;
import com.github.mcollovati.quarkus.hilla.security.VaadinSecurityRuntimeConfiguration;

class QuarkusHillaSecurityProcessor {

    private static final String QUARKUS_ACCESS_PATH_CHECKER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusAccessPathChecker";
    private static final String QUARKUS_HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusHttpPermissionNavigationAccessChecker";

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
            HillaSecurityBuildItem hillaSecurity,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<FilterBuildItem> filters) {
        if (!hillaSecurity.isAuthEnabled()) {
            return;
        }
        AdditionalBeanBuildItem.Builder securityBeans = AdditionalBeanBuildItem.builder()
                .addBeanClass(AnnotationConfigMismatchDiagnostics.class)
                .setDefaultScope(DotNames.APPLICATION_SCOPED)
                .setUnremovable();
        securityBeans
                .addBeanClasses(
                        HillaSecurityPolicy.class,
                        EndpointUtil.class,
                        QuarkusSecurityIdentityCaptureFilter.class,
                        QuarkusSecurityIdentityAugmentor.class)
                .addBeanClass(QUARKUS_ACCESS_PATH_CHECKER);
        filters.produce(FilterBuildItem.builder(
                        QuarkusSecurityIdentityCaptureFilter.class.getName(),
                        QuarkusSecurityIdentityCaptureFilter.class.getName())
                .setAsyncSupported(true)
                .addFilterUrlMapping("/*", DispatcherType.REQUEST)
                .build());
        beans.produce(securityBeans.build());
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
            HillaSecurityBuildItem hillaSecurity,
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer,
            Optional<NavigationAccessControlBuildItem> navigationAccessControlBuildItem) {
        if (hillaSecurity.isAuthEnabled()) {
            recorder.configureNavigationAccessControl(
                    beanContainer.getValue(),
                    navigationAccessControlBuildItem
                            .map(NavigationAccessControlBuildItem::getLoginPath)
                            .orElse(null),
                    hillaSecurity.isFormAuthEnabled());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerAnnotationConfigMismatchConfiguration(
            HillaSecurityBuildItem hillaSecurity,
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        if (!hillaSecurity.isAuthEnabled()) {
            return;
        }
        producer.produce(SyntheticBeanBuildItem.configure(VaadinSecurityRuntimeConfiguration.class)
                .setRuntimeInit()
                .scope(Singleton.class)
                .unremovable()
                .supplier(recorder.setupRuntimeSecurityConfiguration())
                .done());
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
    void registerNavigationAccessControl(
            HillaSecurityBuildItem hillaSecurity,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (hillaSecurity.isAuthEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            QuarkusNavigationAccessControl.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            DefaultAccessCheckDecisionResolver.class)
                    .setUnremovable()
                    .build());
            registerNavigationAccessCheckers(hillaSecurity, accessCheckerProducer);
        }
    }

    void registerNavigationAccessCheckers(
            HillaSecurityBuildItem hillaSecurity,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (!hillaSecurity.isAuthEnabled()) {
            return;
        }
        accessCheckerProducer.produce(new NavigationAccessCheckerBuildItem(
                DotName.createSimple(QUARKUS_HTTP_PERMISSION_NAVIGATION_ACCESS_CHECKER)));
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
}
