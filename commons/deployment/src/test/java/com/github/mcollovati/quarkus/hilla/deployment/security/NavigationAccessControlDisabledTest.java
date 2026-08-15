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

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;
import com.github.mcollovati.quarkus.hilla.security.QuarkusNavigationAccessControl;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAccessControlDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withConfigurationResource(
                    TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
            .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
            .overrideConfigKey("vaadin.security.navigation-access-control.enabled", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(TestUtils.class));

    @Test
    void navigationAccessControlDisabled_noAnnotatedViewAccessChecker() {
        QuarkusNavigationAccessControl accessControl =
                Arc.container().instance(QuarkusNavigationAccessControl.class).get();

        assertThat(accessControl).isNotNull();
        assertThat(accessControl.hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isFalse();
    }
}
