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

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.Route;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.security.test.utils.TestIdentityProvider;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationConfigMismatchFailBootTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.http.auth.basic", "true")
            .overrideConfigKey("vaadin.security.annotation-config-mismatch", "fail")
            .overrideConfigKey("quarkus.http.auth.permission.mismatch.paths", "/mismatch")
            .overrideConfigKey("quarkus.http.auth.permission.mismatch.policy", "permit")
            .setArchiveProducer(() ->
                    ShrinkWrap.create(JavaArchive.class).addClasses(TestIdentityProvider.class, MismatchedView.class))
            .assertException(throwable -> assertThat(throwable)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("/mismatch")
                    .hasMessageContaining("conjunction"));

    @Test
    void startupFailsForProvenMismatch() {
        // The extension assertion verifies the startup failure.
    }

    @Tag("mismatched-view")
    @Route("mismatch")
    @RolesAllowed("USER")
    public static class MismatchedView extends Component {}
}
