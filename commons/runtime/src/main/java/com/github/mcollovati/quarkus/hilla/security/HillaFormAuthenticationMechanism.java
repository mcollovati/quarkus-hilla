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
package com.github.mcollovati.quarkus.hilla.security;

import java.util.Optional;
import java.util.Set;

import io.quarkus.arc.ClientProxy;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class HillaFormAuthenticationMechanism implements HttpAuthenticationMechanism {
    private static final String AUTHENTICATION_MECHANISM_KEY = HttpAuthenticationMechanism.class.getName();

    private final Config config;

    FormAuthenticationMechanism delegate;

    public HillaFormAuthenticationMechanism(FormAuthenticationMechanism delegate, Config config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (context.normalizedPath().equals(config.logoutPath())) {
            logout(context);
            return Uni.createFrom().optional(Optional.empty());
        }
        HttpServerRequest request = context.request();
        if ("typescript".equals(request.getHeader("source"))) {
            HttpServerResponse response = context.response();
            response.headersEndHandler(event -> {
                boolean loginFailure = context.get("typescript-login-failure", false);
                if (response.getStatusCode() == 302 || response.getStatusCode() == 200) {
                    response.setStatusCode(200);
                    String redirectUrl = response.headers().get("Location");
                    response.headers().remove("Location");
                    if (loginFailure) {
                        response.setStatusCode(401);
                    } else {
                        response.putHeader("Default-url", config.landingPage());
                        if (redirectUrl != null) {
                            response.putHeader("Saved-url", redirectUrl);
                        }
                        response.putHeader("Result", "success");
                    }
                }
            });
        }
        Uni<SecurityIdentity> authentication = delegate.authenticate(context, identityProviderManager);
        // FormAuthenticationMechanism selects itself for challenge handling. Restore this wrapper so TypeScript login
        // failures pass through getChallenge and are not converted into successful responses.
        if (ClientProxy.unwrap(context.get(AUTHENTICATION_MECHANISM_KEY)) == ClientProxy.unwrap(delegate)) {
            context.put(AUTHENTICATION_MECHANISM_KEY, this);
        }
        return authentication;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if ("typescript".equals(context.request().getHeader("source"))) {
            context.put("typescript-login-failure", true);
        }
        return delegate.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return delegate.getCredentialTypes();
    }

    private void logout(RoutingContext ctx) {
        // Request cookies do not include path or domain and may be absent when logout is outside the cookie path.
        // Expire the configured scope explicitly so clients clear the authentication cookie in either case.
        Cookie cookie = Cookie.cookie(config.cookieName(), "")
                .setMaxAge(0)
                .setPath(config.cookiePath())
                .setDomain(config.cookieDomain());
        ctx.response().addCookie(cookie);
        if (config.invalidateSessions() && ctx.session() != null) {
            ctx.session().destroy();
        }
        if (config.postLogoutRedirect() != null) {
            ctx.redirect(config.postLogoutRedirect());
        }
    }

    public record Config(
            String cookieName,
            String landingPage,
            String logoutPath,
            String postLogoutRedirect,
            boolean invalidateSessions,
            String cookiePath,
            String cookieDomain) {

        public Config {
            cookiePath = cookiePath == null || cookiePath.isEmpty() ? "/" : cookiePath;
        }

        /**
         * Creates configuration using the default authentication cookie scope.
         *
         * @param cookieName authentication cookie name
         * @param landingPage landing page after login
         * @param logoutPath logout request path
         * @param postLogoutRedirect optional redirect after logout
         * @param invalidateSessions whether logout destroys the session
         */
        public Config(
                String cookieName,
                String landingPage,
                String logoutPath,
                String postLogoutRedirect,
                boolean invalidateSessions) {
            this(cookieName, landingPage, logoutPath, postLogoutRedirect, invalidateSessions, "/", null);
        }
    }
}
