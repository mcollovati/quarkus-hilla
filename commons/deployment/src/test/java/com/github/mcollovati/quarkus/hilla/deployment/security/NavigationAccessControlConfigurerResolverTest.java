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

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.List;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerResolverTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(Configuration.class, ExplicitResolver.class);

    @Test
    void explicitResolver_winsWithoutCreatingCdiFallback() throws ReflectiveOperationException {
        var accessControl = NavigationAccessControlTestSupport.accessControl();
        assertThat(NavigationAccessControlTestSupport.field(
                        accessControl, NavigationAccessControl.class, "decisionResolver"))
                .isInstanceOf(ExplicitResolver.class);
        assertThat(accessControl
                        .checkAccess(NavigationAccessControlTestSupport.context(), false)
                        .reason())
                .isEqualTo("Explicit resolver used");
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer()
                    .withNavigationAccessChecker(NavigationContext::allow)
                    .withDecisionResolver(new ExplicitResolver());
        }

        @Produces
        @Singleton
        AccessCheckDecisionResolver cdiResolver() {
            throw new IllegalStateException("CDI fallback must not be constructed when an explicit resolver is set");
        }
    }

    static class ExplicitResolver implements AccessCheckDecisionResolver {
        @Override
        public AccessCheckResult resolve(List<AccessCheckResult> results, NavigationContext context) {
            return context.deny("Explicit resolver used");
        }
    }
}
