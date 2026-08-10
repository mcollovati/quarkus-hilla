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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.security.identity.SecurityIdentity;

@Singleton
public class QuarkusHttpPermissionNavigationAccessChecker implements NavigationAccessChecker {

    private final transient QuarkusAccessPathChecker pathChecker;
    private final transient QuarkusAnnotatedViewAccessChecker annotatedViewAccessChecker;

    @Inject
    public QuarkusHttpPermissionNavigationAccessChecker(
            QuarkusAccessPathChecker pathChecker, AccessAnnotationChecker accessAnnotationChecker) {
        this(pathChecker, new QuarkusAnnotatedViewAccessChecker(accessAnnotationChecker));
    }

    QuarkusHttpPermissionNavigationAccessChecker(
            QuarkusAccessPathChecker pathChecker, QuarkusAnnotatedViewAccessChecker annotatedViewAccessChecker) {
        this.pathChecker = pathChecker;
        this.annotatedViewAccessChecker = annotatedViewAccessChecker;
    }

    @Override
    public AccessCheckResult check(NavigationContext context) {
        if (context.isErrorHandling()) {
            return context.neutral();
        }

        QuarkusAccessPathChecker.AccessCheck httpCheck = context.isNavigating()
                ? pathChecker.check(
                        context.getLocation().getPathWithQueryParameters(), context.getPrincipal(), context::hasRole)
                : pathChecker.checkCurrentRequest(
                        context.getLocation().getPathWithQueryParameters(), context.getPrincipal(), context::hasRole);
        if (httpCheck.decision() == QuarkusAccessPathChecker.Decision.DENY) {
            return context.deny("Access denied by Quarkus HTTP permission policy " + httpCheck.policyName());
        }

        AccessCheckResult annotationCheck =
                annotatedViewAccessChecker.check(withIdentity(context, httpCheck.identity()));
        if (httpCheck.decision() == QuarkusAccessPathChecker.Decision.ALLOW
                && annotationCheck.decision() == com.vaadin.flow.server.auth.AccessCheckDecision.NEUTRAL) {
            return context.allow();
        }
        return annotationCheck;
    }

    private static NavigationContext withIdentity(NavigationContext context, SecurityIdentity identity) {
        return new NavigationContext(
                context.getRouter(),
                context.getNavigationTarget(),
                context.getLocation(),
                context.getParameters(),
                identity == null || identity.isAnonymous() ? null : identity.getPrincipal(),
                role -> role != null && identity != null && !identity.isAnonymous() && identity.hasRole(role),
                context.isErrorHandling(),
                context.isNavigating());
    }
}
