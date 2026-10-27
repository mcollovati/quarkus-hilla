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

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class AccessAnnotationCheckerDefaultTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = NavigationAccessControlTestSupport.extension();

    @Test
    void formAuthentication_annotatedCheckerUsesDefaultAnnotationChecker() throws ReflectiveOperationException {
        AccessAnnotationChecker annotationChecker =
                Arc.container().instance(AccessAnnotationChecker.class).get();
        AnnotatedViewAccessChecker checker =
                Arc.container().instance(AnnotatedViewAccessChecker.class).get();

        assertThat(annotationChecker.getClass()).isEqualTo(AccessAnnotationChecker.class);
        assertThat(ClientProxy.unwrap(checker).getClass()).isEqualTo(AnnotatedViewAccessChecker.class);
        assertThat(NavigationAccessControlTestSupport.field(
                        ClientProxy.unwrap(checker), AnnotatedViewAccessChecker.class, "accessAnnotationChecker"))
                .isSameAs(annotationChecker);
        assertThat(NavigationAccessControlTestSupport.accessControl()
                        .hasAccessChecker(AnnotatedViewAccessChecker.class))
                .isTrue();
        // The test view has no access annotation, so the default annotation checker denies it.
        assertThat(checker.check(NavigationAccessControlTestSupport.context()).decision())
                .isEqualTo(AccessCheckDecision.DENY);
    }
}
