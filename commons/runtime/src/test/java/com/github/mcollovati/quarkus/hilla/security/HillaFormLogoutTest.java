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

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.PersistentLoginManager;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HillaFormLogoutTest {

    @Test
    void logout_defaultCookiePath_clearsAuthenticationCookie() throws Exception {
        assertLogout("/", null);
    }

    @Test
    void logout_customCookiePath_clearsAuthenticationCookie() throws Exception {
        assertLogout("/app", null);
    }

    @Test
    void logout_customCookiePathAndDomain_expiresMatchingCookie() throws Exception {
        assertLogout("/app", "localhost");
    }

    @Test
    void logout_defaultCookiePathAndCustomDomain_expiresMatchingCookie() throws Exception {
        assertLogout("/", "localhost");
    }

    @Test
    void logout_outsideCookiePath_clearsAuthenticationCookie() throws Exception {
        assertLogout("/app", null, "/logout");
    }

    @Test
    void logout_unsetCookiePathAndNestedLogout_clearsRootAuthenticationCookie() throws Exception {
        assertLogout(null, null);
    }

    @Test
    void logout_emptyCookiePathAndNestedLogout_clearsRootAuthenticationCookie() throws Exception {
        assertLogout("", null);
    }

    private void assertLogout(String cookiePath, String cookieDomain) throws Exception {
        assertLogout(cookiePath, cookieDomain, "/app/logout");
    }

    private void assertLogout(String cookiePath, String cookieDomain, String logoutPath) throws Exception {
        boolean implicitCookiePath = cookiePath == null || cookiePath.isEmpty();
        String loginPath = implicitCookiePath ? "/j_security_check" : "/app/login";
        Vertx vertx = Vertx.vertx();
        try {
            PersistentLoginManager loginManager = new PersistentLoginManager(
                    "logout-test-encryption-key",
                    "auth",
                    60_000,
                    30_000,
                    true,
                    "STRICT",
                    cookiePath,
                    -1L,
                    cookieDomain);
            HillaFormAuthenticationMechanism mechanism = new HillaFormAuthenticationMechanism(
                    mock(FormAuthenticationMechanism.class),
                    new HillaFormAuthenticationMechanism.Config(
                            "auth", "/", logoutPath, null, false, cookiePath, cookieDomain));
            Router router = Router.router(vertx);
            router.get(loginPath).handler(context -> {
                loginManager.save("user", context, "auth", null, false);
                context.response().end();
            });
            router.get(logoutPath).handler(context -> {
                mechanism.authenticate(context, mock(IdentityProviderManager.class));
                context.response().end();
            });
            router.get("/app/protected").handler(context -> context.response()
                    .setStatusCode(loginManager.restore(context) == null ? 401 : 200)
                    .end());
            var server = vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(0, "localhost")
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            URI baseUri = URI.create("http://localhost:" + server.actualPort());
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> login = send(client, baseUri.resolve(loginPath));
            if (implicitCookiePath) {
                assertNull(HttpCookie.parse(
                                login.headers().firstValue("Set-Cookie").orElseThrow())
                        .get(0)
                        .getPath());
            }
            assertEquals(200, send(client, baseUri.resolve("/app/protected")).statusCode());

            HttpResponse<Void> logout = send(client, baseUri.resolve(logoutPath));

            String expiredCookie = logout.headers().firstValue("Set-Cookie").orElseThrow();
            assertEquals(401, send(client, baseUri.resolve("/app/protected")).statusCode(), expiredCookie);
            HttpCookie cookie = HttpCookie.parse(expiredCookie).get(0);
            assertEquals(implicitCookiePath ? "/" : cookiePath, cookie.getPath(), expiredCookie);
            if (cookieDomain == null) {
                assertNull(cookie.getDomain(), expiredCookie);
            } else {
                assertEquals(cookieDomain, cookie.getDomain(), expiredCookie);
            }
            assertEquals(0, cookie.getMaxAge(), expiredCookie);
            assertTrue(cookies.getCookieStore().getCookies().isEmpty(), expiredCookie);
        } finally {
            vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    private HttpResponse<Void> send(HttpClient client, URI uri) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
    }
}
