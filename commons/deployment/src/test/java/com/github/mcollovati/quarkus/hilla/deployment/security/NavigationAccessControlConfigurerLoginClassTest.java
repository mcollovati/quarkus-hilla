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
import java.util.logging.Level;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.security.NavigationAccessControlConfigurer;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlConfigurerLoginClassTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension(
                    Configuration.class, LoginView.class)
            .setLogRecordPredicate(record -> record.getLevel().intValue() >= Level.INFO.intValue())
            .assertLogRecords(records -> assertThat(records).anySatisfy(record -> {
                assertThat(record.getMessage()).contains("Keeping the login view of");
                assertThat(record.getParameters())
                        .contains("NavigationAccessControlConfigurer", LoginView.class.getName(), "/login");
            }));

    @Test
    void loginViewClass_keptAndBothValuesLogged() throws ReflectiveOperationException {
        assertThat(NavigationAccessControlTestSupport.field(
                        NavigationAccessControlTestSupport.accessControl(), NavigationAccessControl.class, "loginView"))
                .isEqualTo(LoginView.class);
    }

    static class Configuration {
        @Produces
        @Singleton
        NavigationAccessControlConfigurer configurer() {
            return new NavigationAccessControlConfigurer()
                    .withAnnotatedViewAccessChecker()
                    .withLoginView(LoginView.class);
        }
    }

    @Tag("div")
    static class LoginView extends Component {}
}
