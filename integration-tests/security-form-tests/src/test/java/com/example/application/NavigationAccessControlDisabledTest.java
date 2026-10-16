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

import java.util.Map;

import com.codeborne.selenide.WebDriverRunner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;

/**
 * Verifies that Flow views become reachable regardless of their access
 * annotations when the navigation access control is turned off.
 */
@QuarkusTest
@TestProfile(NavigationAccessControlDisabledTest.DisabledProfile.class)
class NavigationAccessControlDisabledTest extends AbstractTest {

    public static class DisabledProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("vaadin.security.navigation-access-control.enabled", "false");
        }
    }

    @AfterEach
    void clearBrowser() {
        WebDriverRunner.clearBrowserCache();
    }

    @Test
    void accessControlDisabled_unannotatedFlowView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-default-deny", () -> $("vaadin-app-layout"));

        $("vaadin-app-layout").$("h2").shouldHave(text("Flow - Default deny")).shouldBe(visible);
    }

    @Test
    void accessControlDisabled_protectedFlowView_viewDisplayedWithoutLogin() {
        openAndWait(getTestUrl() + "flow-protected", () -> $("vaadin-app-layout"));

        $("vaadin-login-overlay").shouldNot(exist);
    }
}
