/*
 * Copyright 2023 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.Set;

public class HillaFormAuthenticationMechanism 

    implements HttpAuthenticationMechanism {


    private String logoutPath;
    private String cookieName;

    FormAuthenticationMechanism delegate;

    public HillaFormAuthenticationMechanism(
            FormAuthenticationMechanism delegate, String cookieName, String logoutPath) {
        this.delegate = delegate;
        this.logoutPath = logoutPath;
        this.cookieName = cookieName;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (context.normalizedPath().equals(logoutPath)) {
            logout(context);
            return Uni.createFrom().optional(Optional.empty());
        }
        return delegate.authenticate(context, identityProviderManager);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return delegate.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return delegate.getCredentialTypes();
    }

    private void logout(RoutingContext ctx) {
        // Vert.x sends back a set-cookie with max-age and expiry but no path,
        // so we have to set it first,
        // otherwise web clients don't clear it
        Cookie cookie = ctx.request().getCookie(cookieName);
        if (cookie != null) {
            cookie.setPath("/");
        }
        ctx.response().removeCookie(cookieName);
    }
}
