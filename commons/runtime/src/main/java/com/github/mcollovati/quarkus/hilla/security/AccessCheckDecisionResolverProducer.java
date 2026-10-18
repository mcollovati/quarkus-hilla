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

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import io.quarkus.arc.DefaultBean;

/**
 * Produces the default {@link AccessCheckDecisionResolver} used by
 * {@link QuarkusNavigationAccessControl}.
 * <p>
 * The produced bean is a {@link DefaultBean}, so applications can replace it by
 * providing their own {@link AccessCheckDecisionResolver} bean.
 */
public class AccessCheckDecisionResolverProducer {

    @Produces
    @Singleton
    @DefaultBean
    AccessCheckDecisionResolver accessCheckDecisionResolver() {
        return new DefaultAccessCheckDecisionResolver();
    }
}
