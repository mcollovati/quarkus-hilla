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

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;
import com.github.mcollovati.quarkus.testing.VaadinConditions;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.image;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.example.application.SecurityTest.MenuItem.FLOW_AUTHENTICATED;
import static com.example.application.SecurityTest.MenuItem.FLOW_PUBLIC;
import static com.example.application.SecurityTest.MenuItem.FLOW_USER;
import static com.example.application.SecurityTest.MenuItem.HILLA_AUTHENTICATED;
import static com.example.application.SecurityTest.MenuItem.HILLA_PUBLIC;
import static com.example.application.SecurityTest.MenuItem.HILLA_USER;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SecurityTest extends AbstractTest {

    enum MenuItem {
        HILLA_PUBLIC,
        HILLA_AUTHENTICATED,
        HILLA_USER,
        HILLA_ADMIN,
        FLOW_PUBLIC,
        FLOW_AUTHENTICATED,
        FLOW_USER,
        FLOW_ADMIN;

        public String toString() {
            return name().replace("_", " - ");
        }
    }

    private static EnumSet<MenuItem> ANON_VIEWS = EnumSet.of(HILLA_PUBLIC, FLOW_PUBLIC);
    private static EnumSet<MenuItem> USER_VIEWS =
            EnumSet.of(HILLA_PUBLIC, HILLA_AUTHENTICATED, HILLA_USER, FLOW_PUBLIC, FLOW_AUTHENTICATED, FLOW_USER);
    private static EnumSet<MenuItem> ADMIN_VIEWS = EnumSet.allOf(MenuItem.class);

    SelenideElement appLayout;

    @AfterEach
    void clearBrowser() {
        // clear cookies
        WebDriverRunner.clearBrowserCache();
    }

    @Test
    void anonymous_openHillaPublicView_viewDisplayed() {
        openPublicPage("");
        appLayout.$("h2").shouldHave(Condition.text("Hilla - Public")).shouldBe(visible);
        appLayout.$("img").shouldBe(image);

        assertThatMenuHasItems(ANON_VIEWS);
    }

    @Test
    void anonymous_openFlowPublicView_viewDisplayed() {
        openPublicPage("flow-public");
        appLayout.$("h2").shouldHave(Condition.text("Flow - Public")).shouldBe(visible);
        appLayout.$("img").shouldBe(image);

        assertThatMenuHasItems(ANON_VIEWS);
    }

    @Test
    void anonymous_openProtectedHillaView_loginViewDisplayed() {
        openProtectedPage("hilla-admin", true);
    }

    @Test
    void anonymous_openProtectedFlowView_loginViewDisplayed() {
        openProtectedPage("flow-admin", true);
    }

    @Test
    void authenticatedUser_protectedHillaView_viewDisplayed() {
        openProtectedPage("hilla-protected", true);
        login("user", "user");
        appLayout.$("h2").shouldHave(text("Hilla - Authenticated"));
        assertThatMenuHasItems(USER_VIEWS);
    }

    @Test
    void authenticatedUser_protectedFlowView_viewDisplayed() {
        openProtectedPage("flow-protected", true);
        login("user", "user");
        appLayout.$("h2").shouldHave(text("Flow - Authenticated"));
        assertThatMenuHasItems(USER_VIEWS);
    }

    // user_navigate_to_hilla_user OK
    // user_navigate_to_flow_user OK
    // user_navigate_to_hilla_admin ERR
    // user_navigate_to_flow_admin ERR

    // admin_navigate_everything

    // @Test
    void notAdminUser_adminView_notFoundPage() {
        openAndWait(getTestUrl() + "flow-admin-view", () -> $("vaadin-login-form"));
        login("scott", "pwd");
        $$("div")
                .filter(Condition.text("Could not navigate to 'flow-admin-view'"))
                .first()
                .shouldBe(visible);
    }

    // @Test
    void adminUser_adminView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-admin-view", () -> $("vaadin-login-form"));
        login("stuart", "test");
        $("div#admin-view").shouldBe(visible);
    }

    // @Test
    void notAdminUser_navigateToAdminView_notFoundPage() {
        openAndWait(getTestUrl() + "login", () -> $("vaadin-login-form"));
        login("scott", "pwd");
        $("vaadin-login-form").shouldNot(exist);
        $("vaadin-side-nav").shouldBe(visible);
        $$("vaadin-side-nav-item")
                .filter(VaadinConditions.sideNavItem("/flow-public-view"))
                .first()
                .click();
        $("div#public-view").shouldBe(visible);
        $("a#admin-link").click();
        $$("div")
                .filter(Condition.text("Could not navigate to 'flow-admin-view'"))
                .first()
                .shouldBe(visible);
    }

    protected void openPublicPage(String path) {
        openAndWait(getTestUrl() + path, () -> $("vaadin-app-layout footer a").shouldHave(text("Sign in")));
        appLayout = $("vaadin-app-layout");
    }

    protected void openProtectedPage(String path, boolean expectRedirectToLogin) {
        String selector = expectRedirectToLogin ? "vaadin-login-form" : "vaadin-app-layout footer vaadin-avatar";
        openAndWait(getTestUrl() + path, () -> $(selector));
        appLayout = $("vaadin-app-layout");
    }

    private void assertThatMenuHasItems(Collection<MenuItem> expectedItems) {
        List<String> menuItems = appLayout.$$("vaadin-side-nav-item").texts().stream()
                .map(String::toUpperCase)
                .toList();
        assertThat(menuItems)
                .containsExactly(expectedItems.stream().map(Objects::toString).toArray(String[]::new));
    }

    private void login(String username, String password) {
        SelenideElement loginForm = $("vaadin-login-form").shouldBe(visible);
        loginForm.$("vaadin-text-field#vaadinLoginUsername").setValue(username);
        loginForm.$("vaadin-password-field#vaadinLoginPassword").setValue(password);
        loginForm.$("vaadin-login-form vaadin-button").click();
        $("vaadin-app-layout footer vaadin-avatar").shouldBe(visible, Duration.ofSeconds(10));
    }
}
