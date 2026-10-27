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

import io.quarkus.arc.ClientProxy;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles requests to {@code vaadin.security.logout-path}.
 * <p>
 * Installed as an HTTP filter that runs after authentication, so the logout does not depend on the
 * order of the authentication mechanisms. A form mechanism set up with {@code HttpSecurity} is in
 * the Quarkus mechanism chain twice, and a higher priority lets the plain mechanism restore the
 * identity before {@link HillaFormAuthenticationMechanism} runs.
 */
final class FormLogoutHandler implements Handler<RoutingContext> {

    private static final String AUTHENTICATION_MECHANISM_KEY = HttpAuthenticationMechanism.class.getName();

    private final HillaFormAuthenticationMechanism.Config config;

    FormLogoutHandler(HillaFormAuthenticationMechanism.Config config) {
        this.config = config;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!ctx.normalizedPath().equals(config.logoutPath())) {
            ctx.next();
            return;
        }
        // Also resolves a deferred identity when proactive authentication is disabled, so the form
        // mechanism that restored it has put its cookie scope into the routing context.
        QuarkusHttpUser.getSecurityIdentity(ctx, null)
                .subscribe()
                .with(identity -> logout(ctx, identity), failure -> logout(ctx, null));
    }

    private void logout(RoutingContext ctx, SecurityIdentity identity) {
        try {
            logoutAndContinue(ctx, identity);
        } catch (RuntimeException e) {
            // Mutiny would otherwise drop the exception and leave the request unanswered.
            ctx.fail(e);
        }
    }

    private void logoutAndContinue(RoutingContext ctx, SecurityIdentity identity) {
        if (identity != null && !identity.isAnonymous()) {
            expireAuthenticatingMechanismCookie(ctx);
        }
        // Request cookies do not include path or domain and may be absent when logout is outside the cookie path.
        // Expire the configured scope explicitly so clients clear the authentication cookie in either case.
        // Added last, so it replaces a cookie of the same name, domain and path, including a renewed one.
        Cookie cookie = Cookie.cookie(config.cookieName(), "")
                .setMaxAge(0)
                .setPath(config.cookiePath())
                .setDomain(config.cookieDomain())
                .setSecure(ctx.request().isSSL())
                .setHttpOnly(config.httpOnlyCookie())
                .setSameSite(config.cookieSameSite());
        ctx.response().addCookie(cookie);
        if (config.invalidateSessions() && ctx.session() != null) {
            ctx.session().destroy();
        }
        if (config.postLogoutRedirect() != null) {
            ctx.redirect(config.postLogoutRedirect());
        } else {
            ctx.next();
        }
    }

    // FormAuthenticationMechanism.logout expires the cookie name and path of the form mechanism that
    // authenticated the request, including one set up with HttpSecurity, whose cookie settings are not
    // available otherwise.
    private static void expireAuthenticatingMechanismCookie(RoutingContext ctx) {
        Object mechanism = ClientProxy.unwrap(ctx.get(AUTHENTICATION_MECHANISM_KEY));
        if (mechanism instanceof FormAuthenticationMechanism || mechanism instanceof HillaFormAuthenticationMechanism) {
            try {
                FormAuthenticationMechanism.logout(ctx);
            } catch (NullPointerException missingCookieScope) {
                // Quarkus stores the cookie scope only after the form mechanism restored the identity, and has no
                // cookie path to store with an empty quarkus.http.auth.form.cookie-path. The configured cookie
                // expired below still applies.
            }
        }
    }
}
