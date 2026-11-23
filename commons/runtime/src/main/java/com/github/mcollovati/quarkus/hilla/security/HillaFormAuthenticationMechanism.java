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

import java.util.Set;

import io.quarkus.arc.ClientProxy;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Wraps the form authentication mechanism Quarkus uses, whether it is set up with the
 * {@code quarkus.http.auth.form.*} properties or with {@code HttpSecurity}, to answer login
 * requests of the Hilla TypeScript client. Logout is handled by {@link FormLogoutHandler}.
 */
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

    /**
     * Reports the priority of the wrapped mechanism, so that a priority set on a form mechanism
     * configured with {@code HttpSecurity} or {@code quarkus.http.auth.form.priority} is kept.
     * <p>
     * A form mechanism set up with {@code HttpSecurity} is also in the Quarkus mechanism chain on
     * its own. With equal priorities, Quarkus orders CDI mechanisms such as this wrapper first.
     *
     * @return the priority of the wrapped mechanism
     */
    @Override
    public int getPriority() {
        return delegate.getPriority();
    }

    public record Config(
            String cookieName,
            String landingPage,
            String logoutPath,
            String postLogoutRedirect,
            boolean invalidateSessions,
            String cookiePath,
            String cookieDomain,
            boolean httpOnlyCookie,
            CookieSameSite cookieSameSite) {

        public Config {
            cookiePath = cookiePath == null || cookiePath.isEmpty() ? "/" : cookiePath;
        }

        public Config(
                String cookieName,
                String landingPage,
                String logoutPath,
                String postLogoutRedirect,
                boolean invalidateSessions,
                String cookiePath,
                String cookieDomain) {
            this(
                    cookieName,
                    landingPage,
                    logoutPath,
                    postLogoutRedirect,
                    invalidateSessions,
                    cookiePath,
                    cookieDomain,
                    true,
                    CookieSameSite.STRICT);
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
