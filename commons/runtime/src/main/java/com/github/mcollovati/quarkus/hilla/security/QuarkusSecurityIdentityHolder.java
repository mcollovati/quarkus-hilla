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

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Supplier;

import com.vaadin.flow.server.VaadinRequest;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.quarkus.vertx.http.runtime.security.QuarkusHillaSecurityBridge;
import io.vertx.ext.web.RoutingContext;

/**
 * Captures the Quarkus security identity for Hilla and Flow access checks that
 * run after the original Quarkus request context is no longer visible.
 */
public final class QuarkusSecurityIdentityHolder {

    static final String REQUEST_ATTRIBUTE = QuarkusSecurityIdentityHolder.class.getName() + ".identity";
    static final String NAVIGATION_REQUEST_ATTRIBUTE =
            QuarkusSecurityIdentityHolder.class.getName() + ".navigation-identity";

    private static final ThreadLocal<SecurityIdentity> CURRENT_IDENTITY = new ThreadLocal<>();

    private final Supplier<SecurityIdentity> securityIdentity;

    public QuarkusSecurityIdentityHolder(Supplier<SecurityIdentity> securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    public SecurityIdentity capture(HttpServletRequest request) {
        SecurityIdentity identity = identityFromRequest(request);
        if (identity == null) {
            identity = liveIdentity();
            if (identity != null) {
                request.setAttribute(REQUEST_ATTRIBUTE, identity);
            }
        }
        SecurityIdentity navigationIdentity = navigationIdentity(identity);
        if (navigationIdentity != null) {
            request.setAttribute(NAVIGATION_REQUEST_ATTRIBUTE, navigationIdentity);
        }
        return identity;
    }

    public SecurityIdentity capture(HttpServletRequest request, RoutingContext routingContext) {
        SecurityIdentity identity = capture(request);
        if (identity == null || routingContext == null) {
            return identity;
        }
        if (HttpSecurityUtils.getRoutingContextAttribute(identity) != routingContext) {
            identity = new SecurityIdentityWithAttributes(
                    identity,
                    Map.of(
                            RoutingContext.class.getName(),
                            routingContext,
                            HttpSecurityUtils.ROUTING_CONTEXT_ATTRIBUTE,
                            routingContext));
            request.setAttribute(REQUEST_ATTRIBUTE, identity);
            request.setAttribute(NAVIGATION_REQUEST_ATTRIBUTE, navigationIdentity(identity));
        }
        return identity;
    }

    SecurityIdentity currentIdentity() {
        SecurityIdentity identity = capturedIdentity();
        if (identity != null) {
            return identity;
        }
        return liveIdentity();
    }

    SecurityIdentity capturedIdentity() {
        SecurityIdentity identity = CURRENT_IDENTITY.get();
        if (identity != null) {
            return identity;
        }
        VaadinRequest request = VaadinRequest.getCurrent();
        return request == null ? null : identityFromRequest(request);
    }

    SecurityIdentity currentIdentity(HttpServletRequest request) {
        SecurityIdentity identity = identityFromRequest(request);
        if (identity != null) {
            return identity;
        }
        return currentIdentity();
    }

    SecurityIdentity currentIdentity(VaadinRequest request) {
        SecurityIdentity identity = identityFromRequest(request);
        if (identity != null) {
            return identity;
        }
        return currentIdentity();
    }

    SecurityIdentity currentNavigationIdentity() {
        VaadinRequest request = VaadinRequest.getCurrent();
        if (request != null) {
            SecurityIdentity identity = navigationIdentityFromRequest(request);
            if (identity != null) {
                return identity;
            }
        }
        return navigationIdentity(currentIdentity());
    }

    SecurityIdentity currentNavigationIdentity(VaadinRequest request) {
        SecurityIdentity identity = navigationIdentityFromRequest(request);
        return identity != null ? identity : navigationIdentity(currentIdentity(request));
    }

    public IdentityScope activate(SecurityIdentity identity) {
        SecurityIdentity previous = CURRENT_IDENTITY.get();
        if (identity == null) {
            CURRENT_IDENTITY.remove();
        } else {
            CURRENT_IDENTITY.set(identity);
        }
        return new IdentityScope(previous);
    }

    private SecurityIdentity liveIdentity() {
        try {
            SecurityIdentity identity = CurrentIdentityAssociation.current();
            if (identity != null) {
                identity.isAnonymous();
                return identity;
            }
        } catch (ContextNotActiveException | IllegalStateException | UnsatisfiedResolutionException exception) {
            // Fall back to the injected identity below.
        }
        return identityFromSupplier();
    }

    private SecurityIdentity identityFromSupplier() {
        if (securityIdentity == null) {
            return null;
        }
        try {
            SecurityIdentity identity = securityIdentity.get();
            if (identity == null) {
                return null;
            }
            identity.isAnonymous();
            return identity;
        } catch (ContextNotActiveException | IllegalStateException | UnsatisfiedResolutionException exception) {
            return null;
        }
    }

    private static SecurityIdentity identityFromRequest(HttpServletRequest request) {
        Object identity = request.getAttribute(REQUEST_ATTRIBUTE);
        return identity instanceof SecurityIdentity ? (SecurityIdentity) identity : null;
    }

    private static SecurityIdentity identityFromRequest(VaadinRequest request) {
        Object identity = request.getAttribute(REQUEST_ATTRIBUTE);
        return identity instanceof SecurityIdentity ? (SecurityIdentity) identity : null;
    }

    private static SecurityIdentity navigationIdentityFromRequest(VaadinRequest request) {
        Object identity = request.getAttribute(NAVIGATION_REQUEST_ATTRIBUTE);
        return identity instanceof SecurityIdentity ? (SecurityIdentity) identity : null;
    }

    private static SecurityIdentity navigationIdentity(SecurityIdentity currentIdentity) {
        SecurityIdentity baseIdentity = QuarkusSecurityIdentityAugmentor.baseIdentity(currentIdentity);
        if (baseIdentity == null) {
            return null;
        }
        RoutingContext routingContext = HttpSecurityUtils.getRoutingContextAttribute(currentIdentity);
        return QuarkusHillaSecurityBridge.applyGlobalRolesMapping(routingContext, baseIdentity);
    }

    public final class IdentityScope implements AutoCloseable {

        private final SecurityIdentity previous;

        private IdentityScope(SecurityIdentity previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT_IDENTITY.remove();
            } else {
                CURRENT_IDENTITY.set(previous);
            }
        }
    }
}
