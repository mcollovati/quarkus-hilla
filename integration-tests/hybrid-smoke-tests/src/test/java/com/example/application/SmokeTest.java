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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;
import com.github.mcollovati.quarkus.testing.VaadinConditions;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

@QuarkusTest
class SmokeTest extends AbstractTest {

    @Test
    void rootPath_viewDisplayed() {
        openAndWait(() -> $("div.home-view"));

        $("vaadin-text-field").shouldBe(visible);
        SelenideElement button =
                $$("vaadin-button").filter(Condition.text("Say Hello")).first().shouldBe(visible);
    }

    @Test
    void publicEndpoint_invocationSucceeded() {
        openAndWait(() -> $("div.home-view"));

        SelenideElement textField = $("vaadin-text-field").shouldBe(visible);
        SelenideElement button =
                $$("vaadin-button").filter(Condition.text("Say Hello")).first().shouldBe(visible);

        button.click();
        SelenideElement notification = $("vaadin-notification-card");
        notification.shouldHave(text("Hello from"));
        notification.shouldHave(text("stranger"));

        String name = "Silla";
        textField.$("input").setValue(name);
        button.click();

        notification = $$("vaadin-notification-card").get(1);
        notification.shouldHave(text("Hello from"));
        notification.shouldHave(text(name));
    }

    @Test
    void home_navigateToFlowView_viewDisplayed() {
        openAndWait(() -> $("div.home-view"));
        $$("vaadin-side-nav-item")
                .filter(VaadinConditions.sideNavItem("/flow-public-view"))
                .first()
                .click();
        $("div#public-view").shouldBe(visible);
    }

    @Test
    void openFlowView_viewDisplayed() {
        openAndWait(getTestUrl() + "flow-view", () -> $("span#title"));

        $("vaadin-button").click();
        $("div#time").shouldBe(visible).should(Condition.partialText("Anonymous"));

        Selenide.Wait().pollingEvery(Duration.ofSeconds(3)).until(d -> true);

        $("vaadin-button").click();
        $("div#time").shouldBe(visible).shouldHave(Condition.exactText("Stopped"));
    }

    @Test
    void openNotExisingView_flowErrorPageIsDisplayed() {
        openAndWait(getTestUrl() + "not-existing-view", () -> $("section.view"));

        $$("div")
                .filter(Condition.partialText("Could not navigate to 'not-existing-view'"))
                .first()
                .shouldBe(visible);
    }
}
