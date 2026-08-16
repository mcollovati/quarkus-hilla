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

import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessControl;

/**
 * Replaces the extension provided access control and builds its checker
 * itself, so that the checker is out of reach of the configuration.
 * <p></p>
 * Turning the access control off has to work for this bean as well.
 */
@Singleton
public class CustomNavigationAccessControl extends NavigationAccessControl {

    public CustomNavigationAccessControl() {
        super(List.of(new AnnotatedViewAccessChecker()), new DefaultAccessCheckDecisionResolver());
    }
}
