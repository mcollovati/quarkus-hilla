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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.arc.ClientProxy;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles requests to {@code vaadin.security.logout-path}.
 * <p>
 * Installed as an HTTP filter that runs before authentication, so the logout neither depends on the
 * order of the authentication mechanisms nor on a successful authentication. A form mechanism set up
 * with {@code HttpSecurity} is in the Quarkus mechanism chain twice, and restoring the identity from
 * the cookie may fail, for example for a deleted account.
 * <p>
 * The authentication cookie of the request is expired before authentication, so the form mechanisms
 * find no credential: the logout request is anonymous, and the cookie is neither restored nor renewed.
 * <p>
 * The cookie is expired here with its domain, because {@code FormAuthenticationMechanism.logout} does
 * not set it (https://github.com/quarkusio/quarkus/issues/58042).
 */
final class FormLogoutHandler implements Handler<RoutingContext> {

    private static final String AUTHENTICATION_MECHANISM_KEY = HttpAuthenticationMechanism.class.getName();

    private final HillaFormAuthenticationMechanism.Config config;
    private final Supplier<Optional<CookieScope>> mechanismCookie;
    private volatile Set<CookieScope> cookieScopes;

    FormLogoutHandler(HillaFormAuthenticationMechanism.Config config) {
        this(config, Optional::empty);
    }

    /**
     * @param config the logout settings and the cookie of the {@code quarkus.http.auth.form.*} properties
     * @param mechanismCookie the cookie of the effective form mechanism, read on the first logout
     */
    FormLogoutHandler(HillaFormAuthenticationMechanism.Config config, Supplier<Optional<CookieScope>> mechanismCookie) {
        this.config = config;
        this.mechanismCookie = mechanismCookie;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!ctx.normalizedPath().equals(config.logoutPath())) {
            ctx.next();
            return;
        }
        boolean secureRequest = ctx.request().isSSL();
        Set<CookieScope> scopes = cookieScopes();
        Set<String> neutralizedNames = new LinkedHashSet<>();
        // Vert.x shares the request and response cookie jar. Neutralize incoming credentials once per
        // name before adding any expiry, so a later scope cannot mutate an already queued deletion.
        for (CookieScope scope : scopes) {
            if (neutralizedNames.add(scope.name())) {
                for (Cookie requestCookie : ctx.request().cookies(scope.name())) {
                    scope.applyExpired(requestCookie, secureRequest);
                }
            }
        }
        for (CookieScope scope : scopes) {
            expire(ctx, scope, secureRequest);
        }
        ctx.addHeadersEndHandler(ignored -> expireAuthenticatingMechanismCookie(ctx, scopes, secureRequest));
        if (config.invalidateSessions() && ctx.session() != null) {
            ctx.session().destroy();
        }
        if (config.postLogoutRedirect() != null) {
            ctx.redirect(config.postLogoutRedirect());
        } else {
            ctx.next();
        }
    }

    /**
     * The cookie scopes to expire.
     *
     * @return the scope configured by the {@code quarkus.http.auth.form.*} properties and the scope of
     *     the effective form mechanism, which differs when it is set up with {@code HttpSecurity}
     */
    Set<CookieScope> cookieScopes() {
        Set<CookieScope> scopes = cookieScopes;
        if (scopes == null) {
            scopes = new LinkedHashSet<>();
            scopes.add(CookieScope.of(config));
            mechanismCookie.get().ifPresent(scopes::add);
            cookieScopes = scopes;
        }
        return scopes;
    }

    private static void expire(RoutingContext ctx, CookieScope scope, boolean secureRequest) {
        // Request cookies carry no path or domain, and the cookie is absent when the logout path is outside
        // the cookie path. The expired cookie is therefore always sent with the configured scope. Request
        // cookies have already been neutralized. Fresh response cookies are keyed by name, path and domain.
        ctx.response().addCookie(scope.applyExpired(Cookie.cookie(scope.name(), ""), secureRequest));
    }

    // A cookie that is not among the expired scopes, for example one with a name set up with HttpSecurity,
    // still lets a form mechanism restore the identity. FormAuthenticationMechanism.logout then expires
    // its name and path, which the mechanism put into the routing context.
    private static void expireAuthenticatingMechanismCookie(
            RoutingContext ctx, Set<CookieScope> scopes, boolean secureRequest) {
        Object mechanism = ClientProxy.unwrap(ctx.get(AUTHENTICATION_MECHANISM_KEY));
        if (mechanism instanceof FormAuthenticationMechanism || mechanism instanceof HillaFormAuthenticationMechanism) {
            try {
                FormAuthenticationMechanism.logout(ctx);
                // Quarkus adds a bare name/path expiry, which can replace one of ours. Restore the
                // configured attributes without looking up or mutating cookies in the shared jar.
                for (CookieScope scope : scopes) {
                    expire(ctx, scope, secureRequest);
                }
            } catch (NullPointerException missingCookieScope) {
                // The mechanism restored no identity, or has no cookie path to store with an empty
                // quarkus.http.auth.form.cookie-path. The expired scopes still apply.
            }
        }
    }

    /**
     * The scope of an authentication cookie.
     *
     * @param name the cookie name
     * @param path the cookie path
     * @param domain the cookie domain, or {@code null} for a host-only cookie
     * @param httpOnly whether the cookie is HttpOnly
     * @param sameSite the SameSite attribute
     */
    record CookieScope(String name, String path, String domain, boolean httpOnly, CookieSameSite sameSite) {

        static CookieScope of(HillaFormAuthenticationMechanism.Config config) {
            return new CookieScope(
                    config.cookieName(),
                    config.cookiePath(),
                    config.cookieDomain(),
                    config.httpOnlyCookie(),
                    config.cookieSameSite());
        }

        Cookie applyExpired(Cookie cookie, boolean secureRequest) {
            return cookie.setValue("")
                    .setMaxAge(0)
                    .setPath(path)
                    .setDomain(domain)
                    .setSecure(secureRequest || requiresSecure())
                    .setHttpOnly(httpOnly)
                    .setSameSite(sameSite);
        }

        // Browsers reject these cookies without the Secure attribute, also when expiring them.
        private boolean requiresSecure() {
            return name.startsWith("__Host-") || name.startsWith("__Secure-") || sameSite == CookieSameSite.NONE;
        }
    }
}
