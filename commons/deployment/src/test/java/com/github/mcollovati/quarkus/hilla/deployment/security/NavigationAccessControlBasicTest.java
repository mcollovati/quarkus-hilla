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

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.function.Predicate;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.test.utils.TestIdentityController;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;
import com.github.mcollovati.quarkus.hilla.security.HillaFormAuthenticationMechanism;
import com.github.mcollovati.quarkus.hilla.security.HillaSecurityPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlBasicTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.basic", "true")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .setArchiveProducer(() -> NavigationAccessControlTestSupport.archive(
                    TestIdentityProvider.class, TestIdentityController.class));

    @Test
    void basicAuthentication_activatesSecurityIntegration() {
        NavigationAccessControl accessControl = NavigationAccessControlTestSupport.accessControl();

        assertThat(accessControl.isEnabled()).isTrue();
        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isTrue();
        assertThat(Arc.container().instance(HillaSecurityPolicy.class).isAvailable())
                .isTrue();
        // Views without an access annotation are denied.
        assertThat(accessControl
                        .checkAccess(NavigationAccessControlTestSupport.context(), false)
                        .decision())
                .isEqualTo(AccessCheckDecision.DENY);
    }

    @Test
    void basicAuthentication_noFormMechanismAndNoLoginView() throws ReflectiveOperationException {
        assertThat(Arc.container().select(HttpAuthenticationMechanism.class).stream())
                .noneMatch(HillaFormAuthenticationMechanism.class::isInstance);
        // quarkus.http.auth.form.login-page has a default value, which must not be applied.
        NavigationAccessControl accessControl = NavigationAccessControlTestSupport.accessControl();
        assertThat(NavigationAccessControlTestSupport.field(accessControl, NavigationAccessControl.class, "loginUrl"))
                .isNull();
        assertThat(NavigationAccessControlTestSupport.field(accessControl, NavigationAccessControl.class, "loginView"))
                .isNull();
    }

    @Test
    void anonymousIdentity_hasNoPrincipalAndNoRoles() throws ReflectiveOperationException {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activated = !requestContext.isActive();
        if (activated) {
            requestContext.activate();
        }
        try {
            assertThat(principal()).isNull();
            assertThat(rolesChecker().test("ADMIN")).isFalse();
        } finally {
            if (activated) {
                requestContext.terminate();
            }
        }
    }

    @Test
    void noRequestContext_hasNoPrincipalAndNoRoles() throws ReflectiveOperationException {
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            requestContext.deactivate();
        }
        assertThat(principal()).isNull();
        assertThat(rolesChecker().test("ADMIN")).isFalse();
    }

    private static Principal principal() throws ReflectiveOperationException {
        return (Principal) accessControlMethod("getPrincipal").invoke(accessControl(), (Object) null);
    }

    @SuppressWarnings("unchecked")
    private static Predicate<String> rolesChecker() throws ReflectiveOperationException {
        return (Predicate<String>) accessControlMethod("getRolesChecker").invoke(accessControl(), (Object) null);
    }

    private static NavigationAccessControl accessControl() {
        return NavigationAccessControlTestSupport.accessControl();
    }

    private static Method accessControlMethod(String name) throws NoSuchMethodException {
        Method method = NavigationAccessControl.class.getDeclaredMethod(name, VaadinRequest.class);
        method.setAccessible(true);
        return method;
    }
}
