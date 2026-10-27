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
import java.util.List;

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
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import io.quarkus.vertx.http.deployment.PreRouterFinalizationBuildItem;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.SecurityHandlerPriorities;
import org.eclipse.microprofile.config.ConfigProvider;

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

    // The pages of the form mechanism are read from its fields, see FormLoginSettings.
    @BuildStep
    void registerFormMechanismForReflection(
            AuthFormBuildItem authFormBuildItem, BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        if (authFormBuildItem.isEnabled()) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(FormAuthenticationMechanism.class)
                    .fields()
                    .build());
        }
    }

    // Runs after authentication and before authorization, see FormLogoutHandler.
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerFormLogoutHandler(
            AuthFormBuildItem authFormBuildItem,
            HillaSecurityRecorder recorder,
            BuildProducer<FilterBuildItem> filters) {
        if (authFormBuildItem.isEnabled()) {
            filters.produce(
                    new FilterBuildItem(recorder.formLogoutHandler(), SecurityHandlerPriorities.AUTHORIZATION + 1));
        }
    }

    // The form mechanism bean is read after Quarkus has set up HTTP security, which fires the
    // HttpSecurity event that may configure it.
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(PreRouterFinalizationBuildItem.class)
    void configureHillaSecurityComponents(
            AuthFormBuildItem authFormBuildItem, HillaSecurityRecorder recorder, BeanContainerBuildItem beanContainer) {
        if (authFormBuildItem.isEnabled()) {
            recorder.configureHttpSecurityPolicy(beanContainer.getValue());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    @Consume(PreRouterFinalizationBuildItem.class)
    void configureNavigationAccessControl(
            AuthFormBuildItem authFormBuildItem,
            VaadinSecurityConfig securityConfig,
            HillaSecurityRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        if (authFormBuildItem.isEnabled()) {
            recorder.configureNavigationAccessControl(
                    beanContainer.getValue(),
                    securityConfig.navigationAccessControl().enabled());
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
            AuthFormBuildItem authFormBuildItem, BuildProducer<AdditionalBeanBuildItem> beans) {
        if (authFormBuildItem.isEnabled()) {
            beans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClasses(
                            NavigationAccessControlProducer.class, QuarkusNavigationAccessControl.Installer.class)
                    .setUnremovable()
                    .build());
        }
    }
}
