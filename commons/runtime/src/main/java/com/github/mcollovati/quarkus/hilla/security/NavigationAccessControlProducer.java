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
import java.util.List;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
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
            @All List<NavigationAccessChecker> availableCheckers,
            Instance<AccessCheckDecisionResolver> decisionResolver,
            SecurityIdentity securityIdentity,
            VaadinSecurityConfig securityConfig) {
        return configurer.build(
                availableCheckers,
                decisionResolver::get,
                securityIdentity,
                securityConfig.navigationAccessControl().enabled());
    }
}
