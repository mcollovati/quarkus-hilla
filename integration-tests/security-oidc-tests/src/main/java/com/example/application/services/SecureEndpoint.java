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
package com.example.application.services;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.util.List;

import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import com.vaadin.hilla.BrowserCallable;
import io.quarkus.security.identity.SecurityIdentity;

import com.github.mcollovati.quarkus.hilla.security.QuarkusAccessPathChecker;

@BrowserCallable
public class SecureEndpoint {

    private final SecurityIdentity securityIdentity;
    private final NavigationAccessControl navigationAccessControl;
    private final QuarkusAccessPathChecker accessPathChecker;

    public SecureEndpoint(
            SecurityIdentity securityIdentity,
            NavigationAccessControl navigationAccessControl,
            QuarkusAccessPathChecker accessPathChecker) {
        this.securityIdentity = securityIdentity;
        this.navigationAccessControl = navigationAccessControl;
        this.accessPathChecker = accessPathChecker;
    }

    @AnonymousAllowed
    public String anonymous() {
        return "ANONYMOUS";
    }

    @PermitAll
    public String authenticated() {
        return "AUTHENTICATED:" + securityIdentity.getPrincipal().getName();
    }

    @PermitAll
    public List<String> effectiveRoles() {
        return securityIdentity.getRoles().stream().sorted().toList();
    }

    @RolesAllowed("USER")
    public String userOnly() {
        return "USER";
    }

    @RolesAllowed("ADMIN")
    public String adminOnly() {
        return "ADMIN";
    }

    @DenyAll
    public String denied() {
        throw new IllegalStateException("Method should be denied");
    }

    public String denyByDefault() {
        throw new IllegalStateException("Method should be denied by default");
    }

    @AnonymousAllowed
    public String navigationDecision(String path) {
        NavigationContext context = navigationContext(path);
        if (context.getNavigationTarget() != Object.class) {
            VaadinService service = VaadinService.getCurrent();
            boolean productionMode =
                    service != null && service.getDeploymentConfiguration().isProductionMode();
            return navigationAccessControl
                    .checkAccess(context, productionMode)
                    .decision()
                    .name();
        }
        return accessPathChecker
                .check(path, context.getPrincipal(), context::hasRole)
                .decision()
                .name();
    }

    @RolesAllowed("TARGET_USER")
    public String transportNavigationDecision(String path) {
        return navigationDecision(path);
    }

    private NavigationContext navigationContext(String path) {
        VaadinService service = VaadinService.getCurrent();
        if (service == null) {
            throw new IllegalStateException("VaadinService is not available");
        }
        Router router = service.getRouter();
        RouteRegistry routeRegistry = router.getRegistry();
        String locationPath = path != null && path.startsWith("/") ? path.substring(1) : path;
        String routePath = locationPath == null ? "" : locationPath;
        int queryStart = routePath.indexOf('?');
        if (queryStart >= 0) {
            routePath = routePath.substring(0, queryStart);
        }
        NavigationRouteTarget target = routeRegistry.getNavigationRouteTarget(routePath);
        Class<?> targetView = Object.class;
        RouteParameters routeParameters = RouteParameters.empty();
        if (target != null) {
            RouteTarget routeTarget = target.getRouteTarget();
            if (routeTarget != null && routeTarget.getTarget() != null) {
                targetView = routeTarget.getTarget();
                routeParameters = target.getRouteParameters();
            }
        }
        NavigationContext context = new NavigationContext(
                router,
                targetView,
                new Location(locationPath, QueryParameters.empty()),
                routeParameters,
                securityIdentity.isAnonymous() ? null : securityIdentity.getPrincipal(),
                role -> !securityIdentity.isAnonymous() && securityIdentity.hasRole(role),
                false);
        return context;
    }
}
