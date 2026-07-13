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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class QuarkusSecurityIdentityCaptureFilter implements Filter {

    private final QuarkusSecurityIdentityHolder identityHolder;
    private final Instance<CurrentVertxRequest> currentVertxRequest;

    @Inject
    public QuarkusSecurityIdentityCaptureFilter(
            Instance<SecurityIdentity> securityIdentity, Instance<CurrentVertxRequest> currentVertxRequest) {
        this.identityHolder = new QuarkusSecurityIdentityHolder(securityIdentity::get);
        this.currentVertxRequest = currentVertxRequest;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            RoutingContext routingContext = currentRoutingContext();
            SecurityIdentity identity = identityHolder.capture(httpRequest, routingContext);
            try (QuarkusSecurityIdentityHolder.IdentityScope ignored = identityHolder.activate(identity)) {
                chain.doFilter(request, response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private RoutingContext currentRoutingContext() {
        try {
            return currentVertxRequest.isResolvable()
                    ? currentVertxRequest.get().getCurrent()
                    : null;
        } catch (ContextNotActiveException | IllegalStateException exception) {
            return null;
        }
    }
}
