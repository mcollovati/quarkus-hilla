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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.PersistentLoginManager;
import io.vertx.core.http.CookieSameSite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormLoginSettingsTest {

    @Test
    void defaultCookiePath_usesRfc6265DirectoryWithoutDecodingOrNormalizing() {
        for (String path : List.of("", "relative/path", "/", "/check", "/check?next=/other/path")) {
            assertEquals("/", FormLoginSettings.defaultCookiePath(path), path);
        }
        assertEquals("/app", FormLoginSettings.defaultCookiePath("/app/j_security_check"));
        assertEquals("/app", FormLoginSettings.defaultCookiePath("/app/"));
        assertEquals("/mount/app", FormLoginSettings.defaultCookiePath("/mount/app/check?next=/other/path"));
        assertEquals("/app%2Fchild", FormLoginSettings.defaultCookiePath("/app%2Fchild/check"));
        assertEquals("/app/..", FormLoginSettings.defaultCookiePath("/app/../check"));
        assertEquals("/app/", FormLoginSettings.defaultCookiePath("/app//check"));
    }

    @Test
    void cookieScopes_programmaticImplicitPath_includesMountedPostDirectoryAndRoot() {
        for (String path : new String[] {null, ""}) {
            var scopes = FormLoginSettings.cookieScopes(mechanism(path, "/app/j_security_check"), "/mount/");

            assertEquals(Set.of("/mount/app", "/"), paths(scopes));
            assertTrue(scopes.stream()
                    .allMatch(scope -> scope.name().equals("auth")
                            && scope.domain().equals("example.com")
                            && !scope.httpOnly()
                            && scope.sameSite() == CookieSameSite.LAX));
        }
    }

    @Test
    void cookieScopes_explicitPath_preservesExactScope() {
        assertEquals(
                Set.of("/custom"),
                paths(FormLoginSettings.cookieScopes(mechanism("/custom", "/app/check"), "/mount/")));
    }

    @Test
    void cookieScopes_rootPost_deduplicatesRootExpiry() {
        assertEquals(Set.of("/"), paths(FormLoginSettings.cookieScopes(mechanism(null, "/j_security_check"), "/")));
    }

    @Test
    void cookieScopes_propertyImplicitPath_includesMountedPostDirectoryAndRoot() {
        for (Optional<String> path : List.of(Optional.<String>empty(), Optional.of(""))) {
            FormAuthConfig properties = mock(FormAuthConfig.class);
            when(properties.cookieName()).thenReturn("property-auth");
            when(properties.cookiePath()).thenReturn(path);
            when(properties.cookieDomain()).thenReturn(Optional.empty());
            when(properties.cookieSameSite()).thenReturn(FormAuthConfig.CookieSameSite.STRICT);
            when(properties.httpOnlyCookie()).thenReturn(true);
            when(properties.postLocation()).thenReturn("app/j_security_check");

            var scopes = FormLoginSettings.cookieScopes(properties, "/mount/");

            assertEquals(Set.of("/mount/app", "/"), paths(scopes));
            assertTrue(scopes.stream()
                    .allMatch(scope -> scope.name().equals("property-auth")
                            && scope.domain() == null
                            && scope.httpOnly()
                            && scope.sameSite() == CookieSameSite.STRICT));
        }
    }

    private static Set<String> paths(Set<FormLogoutHandler.CookieScope> scopes) {
        return scopes.stream().map(FormLogoutHandler.CookieScope::path).collect(Collectors.toSet());
    }

    @SuppressWarnings("removal")
    private static FormAuthenticationMechanism mechanism(String path, String postLocation) {
        var manager = new PersistentLoginManager(
                "default-path-test-encryption-key", "auth", 60_000, 30_000, false, "LAX", path, -1L, "example.com");
        return new FormAuthenticationMechanism(
                "/login",
                postLocation,
                "j_username",
                "j_password",
                "/error",
                "/",
                true,
                "redirect",
                "LAX",
                path,
                "example.com",
                manager);
    }
}
