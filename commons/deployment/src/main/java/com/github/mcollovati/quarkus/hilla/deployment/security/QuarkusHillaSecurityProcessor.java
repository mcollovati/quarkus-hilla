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
import java.util.Comparator;
import java.util.List;

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
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
import io.quarkus.vertx.http.deployment.SecurityInformationBuildItem;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.DotName;

import com.github.mcollovati.quarkus.hilla.deployment.security.HillaSecurityBuildItem.SecurityModel;
import com.github.mcollovati.quarkus.hilla.security.AccessCheckDecisionResolverProducer;
import com.github.mcollovati.quarkus.hilla.security.EndpointUtil;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityRecorder;
import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlProducer;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;
import com.github.mcollovati.quarkus.hilla.security.VaadinSecurityConfig;

class QuarkusHillaSecurityProcessor {

    @BuildStep
    AuthFormBuildItem authFormEnabledBuildItem() {
        boolean authFormEnabled = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.auth.form.enabled", Boolean.class)
                .orElse(false);
        return new AuthFormBuildItem(authFormEnabled);
    }

    /**
     * Detects the authentication mechanism that activates the security
     * integration. Form authentication takes precedence; otherwise OIDC, JWT
     * and Basic are taken from the security information Quarkus extensions
     * provide. Quarkus reports Basic only if {@code quarkus.http.auth.basic=true}
     * is set explicitly.
     */
    @BuildStep
    HillaSecurityBuildItem hillaSecurity(
            AuthFormBuildItem authForm, List<SecurityInformationBuildItem> securityInformation) {
        if (authForm.isEnabled()) {
            return new HillaSecurityBuildItem(SecurityModel.FORM);
        }
        return new HillaSecurityBuildItem(securityInformation.stream()
                .map(QuarkusHillaSecurityProcessor::toSecurityModel)
                .min(Comparator.naturalOrder())
                .orElse(SecurityModel.NONE));
    }

    static SecurityModel toSecurityModel(SecurityInformationBuildItem securityInformation) {
        return switch (securityInformation.getSecurityModel()) {
            case basic -> SecurityModel.BASIC;
            case oidc -> SecurityModel.OIDC;
            case jwt -> SecurityModel.JWT;
            // Not in scope: elytron OAuth2 opaque token validation.
            case oauth2 -> SecurityModel.NONE;
        };
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
        // The login and logout handling is specific to form authentication.
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
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        if (hillaSecurity.isEnabled()) {
            recorder.configureHttpSecurityPolicy(beanContainer.getValue(), hillaSecurity.isFormAuthentication());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void configureNavigationAccessControl(
            HillaSecurityBuildItem hillaSecurity,
            VaadinSecurityConfig securityConfig,
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        if (hillaSecurity.isEnabled()) {
            recorder.configureNavigationAccessControl(
                    beanContainer.getValue(),
                    securityConfig.navigationAccessControl().enabled(),
                    hillaSecurity.isFormAuthentication());
        }
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
            BuildProducer<NavigationAccessCheckerBuildItem> accessCheckerProducer) {
        if (hillaSecurity.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            NavigationAccessControlProducer.class,
                            QuarkusNavigationAccessControl.Installer.class,
                            AccessCheckDecisionResolverProducer.class)
                    .setUnremovable()
                    .build());
            accessCheckerProducer.produce(
                    new NavigationAccessCheckerBuildItem(DotName.createSimple(AnnotatedViewAccessChecker.class)));
        }
    }
}
