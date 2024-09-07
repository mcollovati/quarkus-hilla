/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
import com.codeborne.selenide.Selenide;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WindowType;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.id;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
public class SignalsTest extends AbstractTest {

    @BeforeEach
    void openTestPage() {
        openAndWait(getTestUrl() + "SharedNumberSignal", () -> $("h2[slot=\"navbar\"]")
                .shouldHave(text("Shared Number Signal")));
    }

    @Test
    public void shouldUpdateValue_both_on_browser_and_server() {
        for (int i = 0; i < 5; i++) {
            var currentSharedValue = getSharedValue();
            clickButton("increaseSharedValue");
            assertEquals(currentSharedValue + 2, getSharedValue(), 0.0);
            assertEquals(getSharedValue(), fetchSharedValue(), 0.0);
        }

        for (int i = 0; i < 5; i++) {
            var currentCounterValue = getCounterValue();
            clickButton("incrementCounter");
            assertEquals(currentCounterValue + 1, getCounterValue());
            assertEquals(getCounterValue(), fetchCounterValue());
        }
    }

    @Test
    public void shouldUpdateValue_forOtherClients() {
        var currentSharedValue = getSharedValue();
        var currentCounterValue = getCounterValue();
        System.out.println(
                "====================== INITIAL STATE WINDOW 1: " + currentSharedValue + " -- " + currentCounterValue);
        var firstWindowHandle = Selenide.webdriver().driver().getWebDriver().getWindowHandle();

        var secondWindowDriver = Selenide.switchTo().newWindow(WindowType.WINDOW);
        try {
            var secondWindowHandle = secondWindowDriver.getWindowHandle();

            assertNotEquals(firstWindowHandle, secondWindowHandle);

            secondWindowDriver.get(getTestUrl() + "SharedNumberSignal");

            var secondWindowSharedValue = Double.parseDouble(
                    secondWindowDriver.findElement(By.id("sharedValue")).getText());
            assertEquals(currentSharedValue, secondWindowSharedValue, 0.0);

            var secondWindowCounterValue = Long.parseLong(
                    secondWindowDriver.findElement(By.id("counter")).getText());
            assertEquals(currentCounterValue, secondWindowCounterValue);

            // press reset button on the second window
            secondWindowDriver.findElement(By.id("reset")).click();

            secondWindowSharedValue = Double.parseDouble(
                    secondWindowDriver.findElement(By.id("sharedValue")).getText());
            assertEquals(0.5, secondWindowSharedValue, 0.0);

            secondWindowCounterValue = Long.parseLong(
                    secondWindowDriver.findElement(By.id("counter")).getText());
            assertEquals(0, secondWindowCounterValue);

            // check that the first window is also updated:
            Selenide.switchTo().window(firstWindowHandle);
            assertEquals(0.5, getSharedValue(), 0.0);
            assertEquals(0, getCounterValue());

        } finally {
            secondWindowDriver.close();
        }
    }

    private double getSharedValue() {
        return Double.parseDouble(
                $(By.id("sharedValue")).shouldNotBe(Condition.empty).getText());
    }

    private long getCounterValue() {
        return Long.parseLong($(By.id("counter")).shouldNotBe(Condition.empty).getText());
    }

    private double fetchSharedValue() {
        clickButton("fetchSharedValue");
        return Double.parseDouble($("span[id=\"sharedValueFromServer\"]")
                .shouldNotBe(Condition.empty)
                .getText());
    }

    private long fetchCounterValue() {
        clickButton("fetchCounterValue");
        return Long.parseLong($("span[id=\"counterValueFromServer\"]")
                .shouldNotBe(Condition.empty)
                .getText());
    }

    private void clickButton(String id) {
        $$("vaadin-button").findBy(id(id)).click();
    }
}
