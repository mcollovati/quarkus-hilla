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

import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;

/**
 * The pages of the form authentication mechanism Quarkus uses.
 *
 * @param loginPage the login page, or {@code null} if Quarkus does not redirect to a login page
 * @param errorPage the error page, or {@code null} if Quarkus does not redirect to an error page
 * @param landingPage the landing page after login
 * @param postLocation the location the login form is posted to
 */
record FormLoginSettings(String loginPage, String errorPage, String landingPage, String postLocation) {

    /**
     * Reads the settings of the effective form mechanism.
     * <p>
     * Only the post location is available from the mechanism itself, through its credential
     * transport. The pages are taken from the {@code quarkus.http.auth.form.*} properties.
     *
     * @param mechanism the form mechanism Quarkus uses
     * @param properties the {@code quarkus.http.auth.form} configuration
     * @return the settings
     */
    static FormLoginSettings of(FormAuthenticationMechanism mechanism, FormAuthConfig properties) {
        return new FormLoginSettings(
                properties.loginPage().orElse(null),
                properties.errorPage().orElse(null),
                properties.landingPage().orElse("/"),
                postLocation(mechanism).orElse(properties.postLocation()));
    }

    /**
     * The paths that anonymous users must be able to reach to log in.
     *
     * @return the login page, error page and post location, without query strings
     */
    Set<String> publicPaths() {
        Set<String> paths = new LinkedHashSet<>();
        Optional.ofNullable(loginPage).map(FormLoginSettings::withoutQuery).ifPresent(paths::add);
        Optional.ofNullable(errorPage).map(FormLoginSettings::withoutQuery).ifPresent(paths::add);
        paths.add(withoutQuery(postLocation));
        return paths;
    }

    // The form mechanism ignores the routing context and reports its credential transport as an
    // immediate item, read without blocking as the settings may be needed on an event loop thread.
    private static Optional<String> postLocation(FormAuthenticationMechanism mechanism) {
        return Optional.ofNullable(mechanism
                        .getCredentialTransport(null)
                        .subscribeAsCompletionStage()
                        .getNow(null))
                .map(HttpCredentialTransport::getTypeTarget);
    }

    private static String withoutQuery(String path) {
        return path.replaceFirst("\\?.*", "");
    }
}
