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

import jakarta.enterprise.context.ApplicationScoped;

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import com.vaadin.hilla.startup.RouteUnifyingServiceInitListener;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlEnabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .setArchiveProducer(() -> NavigationAccessControlTestSupport.archive(ExtraChecker.class));

    @Test
    void formAuthentication_accessControlEnabledByDefault() {
        NavigationAccessControl accessControl =
                Arc.container().instance(NavigationAccessControl.class).get();

        assertThat(accessControl).isNotNull();
        assertThat(accessControl.isEnabled()).isTrue();
        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isTrue();
        assertThat(accessControl.hasAccessChecker(ExtraChecker.class)).isTrue();
        accessControl.checkAccess(NavigationAccessControlTestSupport.context(), false);
        assertThat(Arc.container().instance(ExtraChecker.class).get().calls()).isEqualTo(1);
    }

    @Test
    void consumers_shareContextualInstance() throws ReflectiveOperationException {
        NavigationAccessControl accessControl = NavigationAccessControlTestSupport.accessControl();
        Object installer = Arc.container()
                .instance(QuarkusNavigationAccessControl.Installer.class)
                .get();
        Object policy = ClientProxy.unwrap(
                Arc.container().instance(HillaSecurityPolicy.class).get());
        Object routeListener = Arc.container()
                .instance(RouteUnifyingServiceInitListener.class, VaadinServiceEnabled.Literal.INSTANCE)
                .get();
        assertThat(NavigationAccessControlTestSupport.field(
                        installer, QuarkusNavigationAccessControl.Installer.class, "accessControl"))
                .isSameAs(accessControl);
        assertThat(NavigationAccessControlTestSupport.field(policy, HillaSecurityPolicy.class, "accessControl"))
                .isSameAs(accessControl);
        assertThat(NavigationAccessControlTestSupport.field(
                        routeListener, RouteUnifyingServiceInitListener.class, "accessControl"))
                .isSameAs(accessControl);
        // The recorder resolved this very instance and applied the form login page.
        assertThat(NavigationAccessControlTestSupport.field(accessControl, NavigationAccessControl.class, "loginUrl"))
                .isEqualTo("/login.html");
        // Only the Flow type is a bean type, so no second instance can exist.
        assertThat(Arc.container()
                        .instance(QuarkusNavigationAccessControl.class)
                        .isAvailable())
                .isFalse();
    }

    @ApplicationScoped
    static class ExtraChecker implements NavigationAccessChecker {
        private int calls;

        @Override
        public AccessCheckResult check(NavigationContext context) {
            calls++;
            return context.neutral();
        }

        int calls() {
            return calls;
        }
    }
}
