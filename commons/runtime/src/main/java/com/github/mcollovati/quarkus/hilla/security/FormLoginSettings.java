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

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import org.slf4j.LoggerFactory;

/**
 * The pages of the form authentication mechanism Quarkus uses.
 *
 * @param loginPage the login page, or {@code null} if Quarkus does not redirect to a login page
 * @param errorPage the error page, or {@code null} if Quarkus does not redirect to an error page
 * @param landingPage the landing page after login
 * @param postLocation the location the login form is posted to
 * @param loginPageFromProperties whether the login page is the one of {@code quarkus.http.auth.form.login-page}
 */
record FormLoginSettings(
        String loginPage, String errorPage, String landingPage, String postLocation, boolean loginPageFromProperties) {

    static final String LOGIN_PAGE_PROPERTY = "quarkus.http.auth.form.login-page";

    /**
     * Reads the settings of the effective form mechanism, set up with the
     * {@code quarkus.http.auth.form.*} properties or with {@code HttpSecurity}.
     * <p>
     * The post location is available through the credential transport of the mechanism. Quarkus
     * has no public API for the pages, so they are read from the fields of
     * {@link FormAuthenticationMechanism}. If that fails, the properties are used and a warning is
     * logged.
     *
     * @param mechanism the form mechanism Quarkus uses
     * @param properties the {@code quarkus.http.auth.form} configuration
     * @return the settings
     */
    static FormLoginSettings of(FormAuthenticationMechanism mechanism, FormAuthConfig properties) {
        String postLocation = postLocation(mechanism).orElse(properties.postLocation());
        String propertyLoginPage = withSlash(properties.loginPage().orElse(null));
        try {
            String loginPage = page(mechanism, "loginPage");
            return new FormLoginSettings(
                    loginPage,
                    page(mechanism, "errorPage"),
                    Objects.requireNonNullElse(page(mechanism, "landingPage"), "/"),
                    postLocation,
                    Objects.equals(loginPage, propertyLoginPage));
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            LoggerFactory.getLogger(FormLoginSettings.class)
                    .warn(
                            "Cannot read the pages of the form authentication mechanism, using the "
                                    + "quarkus.http.auth.form.* properties instead. A login, error or landing page "
                                    + "set up with HttpSecurity is not applied.",
                            unreadable);
            return new FormLoginSettings(
                    propertyLoginPage,
                    withSlash(properties.errorPage().orElse(null)),
                    withSlash(properties.landingPage().orElse("/")),
                    postLocation,
                    true);
        }
    }

    /**
     * Names the login page and where it is configured, for log and error messages.
     *
     * @return for example {@code quarkus.http.auth.form.login-page=/login}
     */
    String describeLoginPage() {
        return loginPageFromProperties
                ? LOGIN_PAGE_PROPERTY + "=" + loginPage
                : "the login page " + loginPage + " of the form mechanism set up with HttpSecurity";
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

    // Quarkus has no getters for the pages; the field names have been stable since Quarkus 1.x.
    // FormAuthenticationMechanism is registered for reflection in native mode.
    private static String page(FormAuthenticationMechanism mechanism, String name) throws ReflectiveOperationException {
        Field field = FormAuthenticationMechanism.class.getDeclaredField(name);
        field.setAccessible(true);
        return (String) field.get(mechanism);
    }

    // FormAuthenticationMechanism prefixes the configured pages with a slash.
    private static String withSlash(String page) {
        return page == null || page.startsWith("/") ? page : "/" + page;
    }

    private static String withoutQuery(String path) {
        return path.replaceFirst("\\?.*", "");
    }
}
