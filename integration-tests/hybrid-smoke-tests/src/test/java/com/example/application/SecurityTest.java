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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.WebDriverRunner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;
import com.github.mcollovati.quarkus.testing.VaadinConditions;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

@QuarkusTest
@TestProfile(SecurityTestProfile.class)
class SecurityTest extends AbstractTest {

    @AfterEach
    void clearBrowser() {
        // clear cookies
        WebDriverRunner.clearBrowserCache();
    }

    @Test
    void anonymous_openPublicView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-public-view", () -> $("div#public-view"));
    }

    @Test
    void anonymous_openProtectedView_loginViewDisplayed() {
        openAndWait(getTestUrl() + "flow-protected-view", () -> $("vaadin-login-form"));
    }

    @Test
    void authenticatedUser_protectedView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-protected-view", () -> $("vaadin-login-form"));
        login("scott", "pwd");
        $("div#protected-view").shouldBe(visible);
    }

    @Test
    void notAdminUser_adminView_notFoundPage() {
        openAndWait(getTestUrl() + "flow-admin-view", () -> $("vaadin-login-form"));
        login("scott", "pwd");
        $$("div")
                .filter(Condition.text("Could not navigate to 'flow-admin-view'"))
                .first()
                .shouldBe(visible);
    }

    @Test
    void adminUser_adminView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-admin-view", () -> $("vaadin-login-form"));
        login("stuart", "test");
        $("div#admin-view").shouldBe(visible);
    }

    @Test
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

    private void login(String username, String password) {
        SelenideElement loginForm = $("vaadin-login-form").shouldBe(visible);
        loginForm.$("vaadin-text-field#vaadinLoginUsername").setValue(username);
        loginForm.$("vaadin-password-field#vaadinLoginPassword").setValue(password);
        loginForm.$("vaadin-login-form vaadin-button").click();
    }
}
