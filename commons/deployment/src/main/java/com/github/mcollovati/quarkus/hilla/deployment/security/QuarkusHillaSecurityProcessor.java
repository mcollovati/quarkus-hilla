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
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import com.github.mcollovati.quarkus.hilla.security.EndpointUtil;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

class QuarkusHillaSecurityProcessor {

    @BuildStep
    AuthFormBuildItem authFormEnabledBuildItem() {
        boolean authFormEnabled = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.auth.form.enabled", Boolean.class)
                .orElse(false);
        return new AuthFormBuildItem(authFormEnabled);
    }

    @BuildStep
    void registerHillaSecurityPolicy(AuthFormBuildItem authFormEnabled, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (authFormEnabled.isEnabled()) {
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
            AuthFormBuildItem authFormBuildItem,
            HillaSecurityRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> producer) {
        if (authFormBuildItem.isEnabled()) {
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
            AuthFormBuildItem authFormBuildItem, HillaSecurityRecorder recorder, BeanContainerBuildItem beanContainer) {
        if (authFormBuildItem.isEnabled()) {
            recorder.configureHttpSecurityPolicy(beanContainer.getValue());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureNavigationAccessControl(
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer,
            Optional<NavigationAccessControlBuildItem> navigationAccessControlBuildItem,
            SecurityConfiguration securityConfiguration) {
        navigationAccessControlBuildItem
                .map(NavigationAccessControlBuildItem::getLoginPath)
                .ifPresent(loginPath -> recorder.configureNavigationAccessControl(
                        beanContainer.getValue(), loginPath, securityConfiguration.restorePathAfterLogin()));
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
            AuthFormBuildItem authFormBuildItem,
            CombinedIndexBuildItem index,
            BuildProducer<AdditionalBeanBuildItem> beans,
            BuildProducer<NavigationAccessControlBuildItem> accessControlProducer,
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (authFormBuildItem.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            QuarkusNavigationAccessControl.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            DefaultAccessCheckDecisionResolver.class)
                    .setUnremovable()
                    .build());
            if (hasSecuredRoutes(index)) {
                accessCheckerProducer.produce(
                        new NavigationAccessCheckerBuildItem(DotName.createSimple(AnnotatedViewAccessChecker.class)));
            }

            ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.http.auth.form.login-page", String.class)
                    .map(NavigationAccessControlBuildItem::new)
                    .ifPresent(accessControlProducer::produce);
        }
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
