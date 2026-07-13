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
package com.example.application;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class FormAuthenticationProtocolTest {

    private static final String LOGIN_FORM = "username=%s&password=%s";

    @TestHTTPResource("/")
    URI baseUri;

    @Test
    void typescriptLogin_invalidCredentials_returnsAuthenticationFailure() throws Exception {
        HttpResponse<Void> response = login(newClient(), "unknown", "wrong", true);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Result")).isEmpty();
        assertThat(response.headers().firstValue("Default-url")).isEmpty();
        assertThat(response.headers().firstValue("Saved-url")).isEmpty();
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void formLogin_invalidCredentials_redirectsToConfiguredErrorPageWithQuery() throws Exception {
        HttpResponse<Void> response = login(newClient(), "unknown", "wrong", false);

        assertThat(response.statusCode()).isEqualTo(302);
        assertRedirect(response, "/login", "error");
        assertThat(response.headers().firstValue("Result")).isEmpty();
    }

    @Test
    void typescriptLogin_validCredentials_returnsSuccessAndConfiguredLandingPage() throws Exception {
        HttpResponse<Void> response = login(newClient(), "user", "user", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Result")).hasValue("success");
        assertThat(response.headers().firstValue("Default-url")).hasValue("/hilla-protected");
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void formLogin_validCredentials_redirectsToConfiguredLandingPage() throws Exception {
        HttpResponse<Void> response = login(newClient(), "user", "user", false);

        assertThat(response.statusCode()).isEqualTo(302);
        assertRedirect(response, "/hilla-protected", null);
        assertThat(response.headers().firstValue("Result")).isEmpty();
    }

    @Test
    void formLogin_directNavigation_enforcesFlowAndHillaRoles() throws Exception {
        HttpClient userClient = newClient();
        assertThat(login(userClient, "user", "user", true).statusCode()).isEqualTo(200);
        // A cold dev-mode frontend request generates the Hilla client-route manifest.
        assertStatus(userClient, "/", 200);
        assertStatus(userClient, "/flow-admin", 403);
        assertStatus(userClient, "/hilla-admin", 403);

        HttpClient adminClient = newClient();
        assertThat(login(adminClient, "admin", "admin", true).statusCode()).isEqualTo(200);
        assertStatus(adminClient, "/flow-admin", 200);
        assertStatus(adminClient, "/hilla-admin", 200);
    }

    @Test
    void typescriptLogin_savedRequest_returnsOriginalUrlIncludingQuery() throws Exception {
        HttpClient client = newClient();
        assertLoginChallenge(client, "/flow-protected?tab=details");

        HttpResponse<Void> response = login(client, "user", "user", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Result")).hasValue("success");
        assertThat(response.headers().firstValue("Default-url")).hasValue("/hilla-protected");
        assertThat(response.headers().firstValue("Saved-url"))
                .hasValueSatisfying(savedUrl -> assertUri(savedUrl, "/flow-protected", "tab=details"));
        assertThat(response.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void typescriptLogin_publicAssetDoesNotReplaceSavedRequest() throws Exception {
        HttpClient client = newClient();
        assertLoginChallenge(client, "/flow-protected?tab=details");

        HttpRequest assetRequest = HttpRequest.newBuilder(baseUri.resolve("images/empty-plant.png"))
                .GET()
                .build();
        HttpResponse<Void> assetResponse = client.send(assetRequest, HttpResponse.BodyHandlers.discarding());
        assertThat(assetResponse.statusCode()).isEqualTo(200);

        HttpResponse<Void> response = login(client, "user", "user", true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Saved-url"))
                .hasValueSatisfying(savedUrl -> assertUri(savedUrl, "/flow-protected", "tab=details"));
    }

    @Test
    void typescriptLogin_invalidThenValid_preservesSavedRequestIncludingQuery() throws Exception {
        HttpClient client = newClient();
        assertLoginChallenge(client, "/flow-protected?tab=details");

        HttpResponse<Void> failedLogin = login(client, "user", "wrong", true);
        assertThat(failedLogin.statusCode()).isEqualTo(401);
        assertThat(failedLogin.headers().firstValue("Result")).isEmpty();
        assertThat(failedLogin.headers().firstValue("Default-url")).isEmpty();
        assertThat(failedLogin.headers().firstValue("Saved-url")).isEmpty();
        assertThat(failedLogin.headers().firstValue("Location")).isEmpty();

        HttpResponse<Void> successfulLogin = login(client, "user", "user", true);
        assertThat(successfulLogin.statusCode()).isEqualTo(200);
        assertThat(successfulLogin.headers().firstValue("Result")).hasValue("success");
        assertThat(successfulLogin.headers().firstValue("Saved-url"))
                .hasValueSatisfying(savedUrl -> assertUri(savedUrl, "/flow-protected", "tab=details"));
    }

    @Test
    void formLogin_savedRequest_redirectsToOriginalUrlIncludingQuery() throws Exception {
        HttpClient client = newClient();
        assertLoginChallenge(client, "/flow-protected?tab=details");

        HttpResponse<Void> response = login(client, "user", "user", false);

        assertThat(response.statusCode()).isEqualTo(302);
        assertRedirect(response, "/flow-protected", "tab=details");
        assertThat(response.headers().firstValue("Result")).isEmpty();
    }

    private HttpClient newClient() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private void assertLoginChallenge(HttpClient client, String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(baseUri.resolve(path)).GET().build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(302);
        assertRedirect(response, "/login", null);
    }

    private void assertStatus(HttpClient client, String path, int expectedStatus)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }

    private HttpResponse<Void> login(HttpClient client, String username, String password, boolean typescript)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve("login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(LOGIN_FORM.formatted(username, password)));
        if (typescript) {
            request.header("source", "typescript");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }

    private void assertRedirect(HttpResponse<?> response, String expectedPath, String expectedQuery) {
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertUri(location, expectedPath, expectedQuery));
    }

    private void assertUri(String location, String expectedPath, String expectedQuery) {
        URI uri = URI.create(location);
        assertThat(uri.getPath()).isEqualTo(expectedPath);
        assertThat(uri.getQuery()).isEqualTo(expectedQuery);
    }
}
