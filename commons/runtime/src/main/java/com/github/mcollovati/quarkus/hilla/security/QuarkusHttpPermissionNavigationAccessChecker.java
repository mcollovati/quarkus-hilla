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

import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;

public class QuarkusHttpPermissionNavigationAccessChecker implements NavigationAccessChecker {

    private final transient QuarkusAccessPathChecker pathChecker;

    public QuarkusHttpPermissionNavigationAccessChecker(QuarkusAccessPathChecker pathChecker) {
        this.pathChecker = pathChecker;
    }

    @Override
    public AccessCheckResult check(NavigationContext context) {
        if (context.isErrorHandling()) {
            return context.neutral();
        }

        QuarkusAccessPathChecker.AccessCheck check = pathChecker.check(
                context.getLocation().getPath(), context.getPrincipal(), context::hasRole);
        return switch (check.decision()) {
            case NO_MATCH -> context.neutral();
            case ALLOW -> context.allow();
            case DENY -> context.deny("Access denied by Quarkus HTTP permission policy " + check.policyName());
        };
    }
}
