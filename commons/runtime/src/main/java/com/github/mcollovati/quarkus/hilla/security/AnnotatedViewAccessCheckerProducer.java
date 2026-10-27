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
package com.github.mcollovati.quarkus.hilla.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import io.quarkus.arc.Unremovable;

/**
 * Produces the built-in {@link AnnotatedViewAccessChecker} used by the
 * navigation access control.
 * <p>
 * As in Vaadin's Spring integration, the checker evaluates the view access
 * annotations with the {@link AccessAnnotationChecker} bean. The extension
 * provides a default one, which applications can replace by providing their own
 * {@link AccessAnnotationChecker} bean.
 */
public class AnnotatedViewAccessCheckerProducer {

    @Produces
    @ApplicationScoped
    @Unremovable
    AnnotatedViewAccessChecker annotatedViewAccessChecker(AccessAnnotationChecker accessAnnotationChecker) {
        return new AnnotatedViewAccessChecker(accessAnnotationChecker);
    }
}
