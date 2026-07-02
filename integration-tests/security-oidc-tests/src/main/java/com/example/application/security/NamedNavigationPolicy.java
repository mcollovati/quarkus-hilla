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
import java.util.function.Function;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class NamedNavigationPolicy implements HttpSecurityPolicy {

    public static final String NAME = "named-navigation-policy";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Uni<CheckResult> checkPermission(
            RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext) {
        return identity.map(new Function<>() {
            @Override
            public CheckResult apply(SecurityIdentity securityIdentity) {
                return !securityIdentity.isAnonymous() && securityIdentity.hasRole("USER")
                        ? CheckResult.PERMIT
                        : CheckResult.DENY;
            }
        });
    }
}
