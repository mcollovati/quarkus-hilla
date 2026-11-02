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
import java.security.Principal;
import java.util.function.Function;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

class AccessAnnotationCheckerCustomTest {

    @RegisterExtension
    static final QuarkusExtensionTest config =
            NavigationAccessControlTestSupport.extension(AllowAllAccessAnnotationChecker.class);

    @Test
    void customAnnotationChecker_replacesDefaultInAnnotatedChecker() throws ReflectiveOperationException {
        AccessAnnotationChecker annotationChecker =
                Arc.container().instance(AccessAnnotationChecker.class).get();
        AnnotatedViewAccessChecker checker =
                Arc.container().instance(AnnotatedViewAccessChecker.class).get();

        assertThat(annotationChecker).isInstanceOf(AllowAllAccessAnnotationChecker.class);
        // The built-in checker keeps its exact class, only the annotation evaluation is replaced.
        assertThat(ClientProxy.unwrap(checker).getClass()).isEqualTo(AnnotatedViewAccessChecker.class);
        assertThat(NavigationAccessControlTestSupport.field(
                        ClientProxy.unwrap(checker), AnnotatedViewAccessChecker.class, "accessAnnotationChecker"))
                .isSameAs(annotationChecker);
        // The test view has no access annotation; the application's annotation checker allows it anyway.
        assertThat(checker.check(NavigationAccessControlTestSupport.context()).decision())
                .isEqualTo(AccessCheckDecision.ALLOW);
        assertThat(((AllowAllAccessAnnotationChecker) annotationChecker).calls())
                .isPositive();
    }

    @Singleton
    static class AllowAllAccessAnnotationChecker extends AccessAnnotationChecker {
        private int calls;

        @Override
        public boolean hasAccess(Class<?> cls, Principal principal, Function<String, Boolean> roleChecker) {
            calls++;
            return true;
        }

        int calls() {
            return calls;
        }
    }
}
