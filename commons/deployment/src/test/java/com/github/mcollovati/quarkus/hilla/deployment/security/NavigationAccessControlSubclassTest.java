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

import jakarta.inject.Singleton;
import java.util.List;

import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A subclass that sets the same login view as the configuration and turns
 * itself off. The extension must accept the equal login view and must not
 * turn the access control on while the setting is left at its default.
 */
class NavigationAccessControlSubclassTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("quarkus.http.auth.form.login-page", "/login")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .setArchiveProducer(() ->
                    ShrinkWrap.create(JavaArchive.class).addClasses(TestUtils.class, SelfDisabledAccessControl.class));

    @Test
    void subclass_sameLoginViewAccepted_ownDisabledStateKept() {
        NavigationAccessControl accessControl =
                Arc.container().instance(NavigationAccessControl.class).get();

        assertThat(accessControl).isInstanceOf(SelfDisabledAccessControl.class);
        assertThat(((SelfDisabledAccessControl) accessControl).loginUrl()).isEqualTo("/login");
        assertThat(accessControl.isEnabled()).isFalse();
    }

    @Singleton
    static class SelfDisabledAccessControl extends QuarkusNavigationAccessControl {

        SelfDisabledAccessControl(
                @All List<NavigationAccessChecker> checkerList,
                AccessCheckDecisionResolver decisionResolver,
                SecurityIdentity securityIdentity) {
            super(checkerList, decisionResolver, securityIdentity);
            setLoginView("/login");
            setEnabled(false);
        }

        String loginUrl() {
            return getLoginUrl();
        }
    }
}
