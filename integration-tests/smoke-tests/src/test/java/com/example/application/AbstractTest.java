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

import static java.time.temporal.ChronoUnit.SECONDS;

import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.junit5.BrowserPerTestStrategyExtension;
import io.quarkus.test.common.http.TestHTTPResource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

@ExtendWith({BrowserPerTestStrategyExtension.class})
public abstract class AbstractTest {

    @TestHTTPResource()
    private String baseURL;

    @BeforeEach
    void setup() {
        Configuration.headless = runHeadless();
        System.setProperty("chromeoptions.args", "--remote-allow-origins=*");
    }

    protected final String getBaseURL() {
        return baseURL;
    }

    protected String getTestUrl() {
        return baseURL;
    }

    protected void open() {
        open(getTestUrl());
    }

    protected void open(String url) {
        Selenide.open(url);
    }

    protected void openAndWait(Supplier<SelenideElement> selector) {
        openAndWait(getTestUrl(), selector);
    }

    protected void openAndWait(String url, Supplier<SelenideElement> selector) {
        Selenide.open(url);
        waitUntilVisible(selector);
    }

    protected void waitUntilPresent(Supplier<SelenideElement> selector) {
        waitUntil(() -> {
            try {
                return selector.get().exists();
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        });
    }

    protected void waitUntilVisible(Supplier<SelenideElement> selector) {
        waitUntil(() -> selector.get().isDisplayed());
    }

    protected <T> T waitUntil(Function<? super WebDriver, T> function) {
        AtomicReference<T> valueHolder = new AtomicReference<>();
        Selenide.Wait().withTimeout(Duration.of(10, SECONDS)).until(driver -> {
            T value = function.apply(driver);
            valueHolder.set(value);
            if (value instanceof Boolean) {
                return value;
            }
            return valueHolder.get() != null;
        });
        return valueHolder.get();
    }

    protected <T> T waitUntil(Supplier<T> function) {
        AtomicReference<T> valueHolder = new AtomicReference<>();
        Selenide.Wait().withTimeout(Duration.of(10, SECONDS)).until(driver -> {
            T value = function.get();
            valueHolder.set(value);
            if (value instanceof Boolean) {
                return value;
            }
            return valueHolder.get() != null;
        });
        return valueHolder.get();
    }

    protected boolean runHeadless() {
        return !isJavaInDebugMode();
    }

    static boolean isJavaInDebugMode() {
        return ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .toString()
                .contains("jdwp");
    }
}
