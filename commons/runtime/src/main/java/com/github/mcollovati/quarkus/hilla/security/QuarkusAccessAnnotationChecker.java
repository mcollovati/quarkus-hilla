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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import io.quarkus.runtime.LaunchMode;

/**
 * Access annotation checker that caches immutable annotation target resolution
 * outside development mode.
 * <p>
 * Access decisions, identities and roles are never cached. Development mode
 * deliberately bypasses the cache so both regular and instrumentation-based
 * hot replacement always observe the latest annotations.
 */
public final class QuarkusAccessAnnotationChecker extends AccessAnnotationChecker {

    private static final AccessAnnotationChecker DELEGATE = new AccessAnnotationChecker();
    private static final ClassValue<AnnotatedElement> SECURITY_TARGETS = new ClassValue<>() {
        @Override
        protected AnnotatedElement computeValue(Class<?> type) {
            return DELEGATE.getSecurityTarget(type);
        }
    };
    private static final ClassValue<ConcurrentMap<Method, AnnotatedElement>> METHOD_SECURITY_TARGETS =
            new ClassValue<>() {
                @Override
                protected ConcurrentMap<Method, AnnotatedElement> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };

    private final boolean cacheEnabled;

    public QuarkusAccessAnnotationChecker() {
        this(LaunchMode.current() != LaunchMode.DEVELOPMENT);
    }

    QuarkusAccessAnnotationChecker(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public AnnotatedElement getSecurityTarget(Class<?> type) {
        return cacheEnabled ? SECURITY_TARGETS.get(type) : super.getSecurityTarget(type);
    }

    @Override
    public AnnotatedElement getSecurityTarget(Method method) {
        if (!cacheEnabled) {
            return super.getSecurityTarget(method);
        }
        return METHOD_SECURITY_TARGETS
                .get(method.getDeclaringClass())
                .computeIfAbsent(method, DELEGATE::getSecurityTarget);
    }
}
