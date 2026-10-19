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
import java.util.logging.Level;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
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
 * A subclass that sets a Flow login view class. Its route cannot be compared
 * with the configured login page at startup, so the class is kept and the
 * startup continues, also with the access control turned off.
 */
class NavigationAccessControlLoginViewClassTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("quarkus.http.auth.form.login-page", "/login")
            .overrideConfigKey("vaadin.security.navigation-access-control.enabled", "false")
            .overrideConfigKey("quarkus.http.test-port", "0")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestUtils.class, LoginView.class, LoginViewClassAccessControl.class))
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.INFO.intValue())
            .assertLogRecords(records -> assertThat(records)
                    .anySatisfy(record -> assertThat(record.getMessage()).contains("Keeping the login view of")));

    @Test
    void loginViewClass_keptAndStartupContinues() {
        NavigationAccessControl accessControl =
                Arc.container().instance(NavigationAccessControl.class).get();

        assertThat(accessControl).isInstanceOf(LoginViewClassAccessControl.class);
        assertThat(((LoginViewClassAccessControl) accessControl).loginViewClass())
                .isEqualTo(LoginView.class);
        assertThat(accessControl.isEnabled()).isFalse();
    }

    @Tag("div")
    static class LoginView extends Component {}

    @Singleton
    static class LoginViewClassAccessControl extends QuarkusNavigationAccessControl {

        LoginViewClassAccessControl(
                @All List<NavigationAccessChecker> checkerList,
                AccessCheckDecisionResolver decisionResolver,
                SecurityIdentity securityIdentity) {
            super(checkerList, decisionResolver, securityIdentity);
            setLoginView(LoginView.class);
        }

        Class<?> loginViewClass() {
            return getLoginView();
        }
    }
}
