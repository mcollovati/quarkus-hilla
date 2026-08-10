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
package com.example.application.security;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityUtils;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class GlobalNavigationDenyPolicy implements HttpSecurityPolicy {

    private static final String DENIED_PATH = "/flow-global-denied";

    @Override
    public Uni<CheckResult> checkPermission(
            RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext) {
        return DENIED_PATH.equals(HttpSecurityUtils.normalizePath(request.normalizedPath()))
                ? CheckResult.deny()
                : CheckResult.permit();
    }
}
