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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteUtil;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationContext;

/**
 * Vaadin annotation checker variant that stays neutral for completely
 * unannotated routes, so Quarkus HTTP permission rules can own those routes.
 */
public class QuarkusAnnotatedViewAccessChecker extends AnnotatedViewAccessChecker {

    private final AccessAnnotationChecker accessAnnotationChecker;

    public QuarkusAnnotatedViewAccessChecker() {
        this(new QuarkusAccessAnnotationChecker());
    }

    QuarkusAnnotatedViewAccessChecker(AccessAnnotationChecker accessAnnotationChecker) {
        super(accessAnnotationChecker);
        this.accessAnnotationChecker = accessAnnotationChecker;
    }

    @Override
    public AccessCheckResult check(NavigationContext context) {
        if (context.isErrorHandling()) {
            return super.check(context);
        }
        try {
            return checkResolvedRoute(context);
        } catch (RuntimeException exception) {
            return context.deny("Exact route security annotations cannot be evaluated");
        }
    }

    private AccessCheckResult checkResolvedRoute(NavigationContext context) {
        Class<?> target = context.getNavigationTarget();
        RouteRegistry registry = context.getRouter().getRegistry();
        String routePath = context.getLocation().getPath();
        routePath = PathUtil.removeLeadingSlashes(routePath);
        NavigationRouteTarget resolvedRoute = registry.getNavigationRouteTarget(routePath);
        if (resolvedRoute == null
                || !resolvedRoute.hasTarget()
                || !target.equals(resolvedRoute.getRouteTarget().getTarget())) {
            return context.deny("Exact route security annotations cannot be resolved");
        }
        List<Class<? extends RouterLayout>> parentLayouts =
                new ArrayList<>(resolvedRoute.getRouteTarget().getParentLayouts());
        if (RouteUtil.isAutolayoutEnabled(target, routePath)) {
            if (parentLayouts.isEmpty() && registry.hasLayout(routePath)) {
                parentLayouts.add(registry.getLayout(routePath));
            }
        }
        boolean annotationPresent =
                hasSecurityAnnotation(target) || parentLayouts.stream().anyMatch(this::hasSecurityAnnotation);
        if (!annotationPresent) {
            return context.neutral();
        }
        for (Class<? extends RouterLayout> layout : parentLayouts) {
            if (!accessAnnotationChecker.hasAccess(layout, context.getPrincipal(), role -> context.hasRole(role))) {
                return context.deny("Access is denied by annotations on parent layout " + layout.getName());
            }
        }
        return accessAnnotationChecker.hasAccess(target, context.getPrincipal(), role -> context.hasRole(role))
                ? context.allow()
                : context.deny("Access is denied by annotations on the view");
    }

    private boolean hasSecurityAnnotation(Class<?> type) {
        AnnotatedElement target = accessAnnotationChecker.getSecurityTarget(type);
        return target.isAnnotationPresent(AnonymousAllowed.class)
                || target.isAnnotationPresent(PermitAll.class)
                || target.isAnnotationPresent(DenyAll.class)
                || target.isAnnotationPresent(RolesAllowed.class);
    }
}
