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
package com.github.mcollovati.quarkus.hilla.security;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Produces the default navigation access control and its configuration.
 * <p>
 * The access control is exposed as {@link NavigationAccessControl} only, so
 * {@link QuarkusNavigationAccessControl} is not a bean type.
 */
public class NavigationAccessControlProducer {

    @Produces
    @Singleton
    @DefaultBean
    NavigationAccessControlConfigurer navigationAccessControlConfigurer() {
        return new NavigationAccessControlConfigurer().withAvailableNavigationAccessCheckers(checker -> true);
    }

    @Produces
    @Singleton
    @DefaultBean
    NavigationAccessControl navigationAccessControl(
            NavigationAccessControlConfigurer configurer,
            @All List<InstanceHandle<NavigationAccessChecker>> availableCheckers,
            @All List<InstanceHandle<AnnotatedViewAccessChecker>> annotatedCheckers,
            Instance<AccessCheckDecisionResolver> decisionResolver,
            SecurityIdentity securityIdentity,
            VaadinSecurityConfig securityConfig) {
        return configurer.build(
                withAnnotatedCheckers(availableCheckers, annotatedCheckers),
                decisionResolver::get,
                securityIdentity,
                securityConfig.navigationAccessControl().enabled());
    }

    // The built-in annotated checker is a default bean of its own type only, so it
    // is not part of the checkers resolved as NavigationAccessChecker. A bean found
    // through both lists is instantiated once, which matters for @Dependent beans.
    private static List<NavigationAccessChecker> withAnnotatedCheckers(
            List<InstanceHandle<NavigationAccessChecker>> availableCheckers,
            List<InstanceHandle<AnnotatedViewAccessChecker>> annotatedCheckers) {
        Set<InjectableBean<?>> seen = new HashSet<>();
        List<NavigationAccessChecker> checkers = new ArrayList<>();
        for (InstanceHandle<AnnotatedViewAccessChecker> handle : annotatedCheckers) {
            if (seen.add(handle.getBean())) {
                checkers.add(handle.get());
            }
        }
        for (InstanceHandle<NavigationAccessChecker> handle : availableCheckers) {
            if (seen.add(handle.getBean())) {
                checkers.add(handle.get());
            }
        }
        return checkers;
    }
}
