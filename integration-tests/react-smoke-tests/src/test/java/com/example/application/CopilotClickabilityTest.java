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

import java.time.Duration;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@QuarkusTest
@Tag("development-only")
class CopilotClickabilityTest extends AbstractTest {

    @Override
    protected boolean copilotEnabled() {
        return true;
    }

    @Test
    void copilotEnabled_rootViewControlsStayClickable() {
        assumeTrue("development".equals(System.getProperty("quarkus-hilla.test-mode")));

        openAndWait(() -> $("vaadin-app-layout"));
        $("copilot-main").shouldBe(exist, Duration.ofSeconds(30));

        SelenideElement textField = $("vaadin-text-field").shouldBe(visible);
        SelenideElement input = textField.$("input").shouldBe(visible);
        SelenideElement button =
                $$("vaadin-button").filter(Condition.text("Say hello")).first().shouldBe(visible);

        String name = "Copilot";
        input.click();
        input.sendKeys(name);
        button.click();

        $("vaadin-notification-card").shouldHave(text("Hello " + name));
    }
}
