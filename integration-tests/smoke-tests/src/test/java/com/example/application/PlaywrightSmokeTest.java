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
package com.example.application;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.HashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Disabled("Test example with Playwright")
class PlaywrightSmokeTest {

    @TestHTTPResource()
    String baseURL;

    static Playwright playwright;
    static Browser browser;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        Playwright.CreateOptions options = new Playwright.CreateOptions();
        HashMap<String, String> env = new HashMap<>();
        env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "true");
        options.setEnv(env);
        playwright = Playwright.create(options);
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void rootPath_viewDisplayed() {
        page.navigate(baseURL);
        assertThat(page.getByLabel("Your name")).isVisible();

        Locator sayHelloButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Say hello"));
        assertThat(sayHelloButton).isVisible();
    }

    @Test
    void publicEndpoint_invocationSucceeded() {
        page.navigate(baseURL);
        Locator textField = page.getByLabel("Your name");
        Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Say hello"));

        button.click();
        Locator notification = page.locator("vaadin-notification-card");
        assertThat(notification).containsText("Hello from");
        assertThat(notification).containsText("stranger");

        String name = "Silla";
        textField.fill(name);
        button.click();

        notification = page.locator("vaadin-notification-card").nth(1);
        assertThat(notification).containsText("Hello from");
        assertThat(notification).containsText(name);
    }
}
