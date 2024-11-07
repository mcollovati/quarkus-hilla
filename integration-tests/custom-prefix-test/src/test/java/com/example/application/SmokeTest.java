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
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

@QuarkusTest
class SmokeTest extends AbstractTest {

    @Test
    void rootPath_viewDisplayed() {
        openAndWait(() -> $("vaadin-app-layout"));

        $("vaadin-text-field").shouldBe(visible);
        SelenideElement button =
                $$("vaadin-button").filter(Condition.text("Say Hello")).first().shouldBe(visible);
    }

    @Test
    void publicEndpoint_invocationSucceeded() {
        openAndWait(() -> $("vaadin-app-layout"));

        SelenideElement textField = $("vaadin-text-field").shouldBe(visible);
        SelenideElement button =
                $$("vaadin-button").filter(Condition.text("Say Hello")).first().shouldBe(visible);

        button.click();
        SelenideElement notification = $("vaadin-notification-card");
        notification.shouldHave(text("Hello stranger"));

        String name = "Silla";
        textField.$("input").setValue(name);
        button.click();

        notification = $$("vaadin-notification-card").get(1);
        notification.shouldHave(text("Hello " + name));
    }

    @Test
    void openNotExisingView_errorPageIsDisplayed() {
        openAndWait(getTestUrl() + "not-existing-view", () -> $("div#outlet"), false);

        $$("h3").filter(Condition.text("Page not found")).first().shouldBe(visible);
    }
}
