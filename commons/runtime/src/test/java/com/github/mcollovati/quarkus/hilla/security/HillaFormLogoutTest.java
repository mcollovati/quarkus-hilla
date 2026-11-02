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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.PersistentLoginManager;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.CookieJar;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.http.impl.Http1xServerResponse;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HillaFormLogoutTest {

    @Test
    void logout_sharedCookieName_expiresEveryPath() throws Exception {
        for (String incoming : List.of("", "auth=encrypted-credential")) {
            LogoutExchange exchange = logoutExchange(incoming);
            var handler = new FormLogoutHandler(
                    new HillaFormAuthenticationMechanism.Config("auth", "/", "/logout", null, false),
                    () -> Optional.of(
                            new FormLogoutHandler.CookieScope("auth", "/app", null, true, CookieSameSite.STRICT)));

            handler.handle(exchange.context());

            List<HttpCookie> expired = exchange.finish();
            assertEquals(2, expired.size());
            assertEquals(
                    Set.of("/", "/app"),
                    expired.stream().map(HttpCookie::getPath).collect(Collectors.toSet()));
            assertTrue(expired.stream().allMatch(cookie -> cookie.getValue().isEmpty() && cookie.getMaxAge() == 0));
            verify(exchange.request()).cookies("auth");
        }
    }

    @Test
    void logout_sharedCookieName_expiresEveryDomain() throws Exception {
        LogoutExchange exchange = logoutExchange("auth=encrypted-credential");
        var handler = new FormLogoutHandler(
                new HillaFormAuthenticationMechanism.Config("auth", "/", "/logout", null, false),
                () -> Optional.of(
                        new FormLogoutHandler.CookieScope("auth", "/", "example.com", true, CookieSameSite.STRICT)));

        handler.handle(exchange.context());

        List<HttpCookie> expired = exchange.finish();
        assertEquals(2, expired.size());
        assertTrue(expired.stream().anyMatch(cookie -> cookie.getDomain() == null));
        assertTrue(expired.stream().anyMatch(cookie -> "example.com".equals(cookie.getDomain())));
        assertTrue(expired.stream().allMatch(cookie -> cookie.getValue().isEmpty() && cookie.getMaxAge() == 0));
    }

    @Test
    void logout_authenticatingMechanism_preservesScopedExpiryAttributes() throws Exception {
        LogoutExchange exchange = logoutExchange("__Secure-auth=encrypted-credential");
        when(exchange.context().get(HttpAuthenticationMechanism.class.getName()))
                .thenReturn(mock(FormAuthenticationMechanism.class));
        when(exchange.context().get("io.quarkus.vertx.http.runtime.security.form.cookie-name"))
                .thenReturn("__Secure-auth");
        when(exchange.context().get("io.quarkus.vertx.http.runtime.security.form.cookie-path"))
                .thenReturn("/");
        var handler = new FormLogoutHandler(
                new HillaFormAuthenticationMechanism.Config(
                        "__Secure-auth", "/", "/logout", null, false, "/", null, true, CookieSameSite.STRICT),
                () -> Optional.of(
                        new FormLogoutHandler.CookieScope("__Secure-auth", "/app", null, true, CookieSameSite.STRICT)));

        handler.handle(exchange.context());

        List<HttpCookie> expired = exchange.finish();
        assertEquals(2, expired.size());
        assertTrue(expired.stream()
                .allMatch(cookie -> cookie.getValue().isEmpty()
                        && cookie.getMaxAge() == 0
                        && cookie.getSecure()
                        && cookie.isHttpOnly()));
        assertTrue(exchange.response().headers().getAll("Set-Cookie").stream()
                .allMatch(header -> header.contains("SameSite=Strict")));
    }

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
    void logout_unsetCookiePathWithRootLogin_clearsRootAuthenticationCookie() throws Exception {
        assertLogout(null, null);
    }

    @Test
    void logout_emptyCookiePathWithRootLogin_clearsRootAuthenticationCookie() throws Exception {
        assertLogout("", null);
    }

    @Test
    void logout_httpsPrefixedCookies_preservesRequiredAttributes() {
        for (String name : new String[] {"__Host-auth", "__Secure-auth"}) {
            RoutingContext context = mock(RoutingContext.class);
            HttpServerRequest request = mock(HttpServerRequest.class);
            HttpServerResponse response = mock(HttpServerResponse.class);
            when(context.normalizedPath()).thenReturn("/logout");
            when(context.request()).thenReturn(request);
            when(context.response()).thenReturn(response);
            when(request.isSSL()).thenReturn(true);
            var handler = new FormLogoutHandler(new HillaFormAuthenticationMechanism.Config(
                    name, "/", "/logout", null, false, "/", null, true, CookieSameSite.STRICT));

            handler.handle(context);

            ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookie.capture());
            assertTrue(cookie.getValue().isSecure());
            assertTrue(cookie.getValue().isHttpOnly());
            assertEquals(CookieSameSite.STRICT, cookie.getValue().getSameSite());
            assertEquals("/", cookie.getValue().getPath());
            assertNull(cookie.getValue().getDomain());
            assertEquals(0, cookie.getValue().getMaxAge());
        }
    }

    @Test
    void logout_httpPrefixedCookies_setsSecure() {
        for (String name : new String[] {"__Host-auth", "__Secure-auth"}) {
            RoutingContext context = mock(RoutingContext.class);
            HttpServerRequest request = mock(HttpServerRequest.class);
            HttpServerResponse response = mock(HttpServerResponse.class);
            when(context.normalizedPath()).thenReturn("/logout");
            when(context.request()).thenReturn(request);
            when(context.response()).thenReturn(response);
            var handler = new FormLogoutHandler(new HillaFormAuthenticationMechanism.Config(
                    name, "/", "/logout", null, false, "/", null, true, CookieSameSite.STRICT));

            handler.handle(context);

            ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
            verify(response).addCookie(cookie.capture());
            assertTrue(cookie.getValue().isSecure(), name);
        }
    }

    @Test
    void logout_requestCookie_expiredBeforeAuthentication() {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        Cookie requestCookie = Cookie.cookie("auth", "encrypted-credential");
        when(context.normalizedPath()).thenReturn("/logout");
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(mock(HttpServerResponse.class));
        when(request.cookies("auth")).thenReturn(Set.of(requestCookie));
        var handler = new FormLogoutHandler(new HillaFormAuthenticationMechanism.Config(
                "auth", "/", "/logout", null, false, "/app", "example.com", true, CookieSameSite.LAX));

        handler.handle(context);

        assertEquals("", requestCookie.getValue());
        assertEquals(0, requestCookie.getMaxAge());
        assertEquals("/app", requestCookie.getPath());
        assertEquals("example.com", requestCookie.getDomain());
        verify(context).next();
    }

    @Test
    void handle_otherPath_passesRequestOn() {
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenReturn("/app");
        var handler =
                new FormLogoutHandler(new HillaFormAuthenticationMechanism.Config("auth", "/", "/logout", null, false));

        handler.handle(context);

        verify(context).next();
        verify(context, never()).response();
    }

    @Test
    void handle_postLogoutRedirect_redirectsInsteadOfPassingOn() {
        RoutingContext context = mock(RoutingContext.class);
        when(context.normalizedPath()).thenReturn("/logout");
        when(context.request()).thenReturn(mock(HttpServerRequest.class));
        when(context.response()).thenReturn(mock(HttpServerResponse.class));
        var handler = new FormLogoutHandler(
                new HillaFormAuthenticationMechanism.Config("auth", "/", "/logout", "/goodbye", false));

        handler.handle(context);

        verify(context).redirect("/goodbye");
        verify(context, never()).next();
    }

    // Exercise the pinned Vert.x cookie jar and actual Set-Cookie serialization without opening a socket.
    private static LogoutExchange logoutExchange(String incoming) throws Exception {
        var nettyRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/logout");
        nettyRequest.headers().set("Cookie", incoming);
        var constructor = Http1xServerResponse.class.getDeclaredConstructor(
                VertxInternal.class,
                ContextInternal.class,
                Http1xServerConnection.class,
                io.netty.handler.codec.http.HttpRequest.class,
                Object.class,
                boolean.class,
                boolean.class);
        constructor.setAccessible(true);
        Http1xServerResponse response =
                constructor.newInstance(null, null, mock(Http1xServerConnection.class), nettyRequest, null, true, true);
        var cookiesMethod = Http1xServerResponse.class.getDeclaredMethod("cookies");
        cookiesMethod.setAccessible(true);
        CookieJar cookies = (CookieJar) cookiesMethod.invoke(response);
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.normalizedPath()).thenReturn("/logout");
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(response);
        when(request.cookies(anyString()))
                .thenAnswer(invocation -> Set.copyOf(cookies.getAll(invocation.getArgument(0, String.class))));
        return new LogoutExchange(context, request, response);
    }

    private record LogoutExchange(RoutingContext context, HttpServerRequest request, Http1xServerResponse response) {
        @SuppressWarnings("unchecked")
        List<HttpCookie> finish() throws Exception {
            ArgumentCaptor<Handler<Void>> callback = ArgumentCaptor.forClass(Handler.class);
            verify(context).addHeadersEndHandler(callback.capture());
            callback.getValue().handle(null);
            var prepareHeaders = Http1xServerResponse.class.getDeclaredMethod("prepareHeaders", long.class);
            prepareHeaders.setAccessible(true);
            prepareHeaders.invoke(response, 0L);
            return response.headers().getAll("Set-Cookie").stream()
                    .map(header -> HttpCookie.parse(header).get(0))
                    .toList();
        }
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
            FormLogoutHandler handler = new FormLogoutHandler(new HillaFormAuthenticationMechanism.Config(
                    "auth", "/", logoutPath, null, false, cookiePath, cookieDomain));
            Router router = Router.router(vertx);
            router.get(loginPath).handler(context -> {
                loginManager.save("user", context, "auth", null, false);
                context.response().end();
            });
            router.get(logoutPath).handler(handler);
            router.get(logoutPath).handler(context -> {
                assertNull(loginManager.restore(context));
                context.response().end();
            });
            router.get("/app/protected")
                    .handler(context -> context.response()
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
