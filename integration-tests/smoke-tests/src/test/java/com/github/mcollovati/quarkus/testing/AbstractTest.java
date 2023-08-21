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
package com.github.mcollovati.quarkus.testing;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.Selectors;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.junit5.BrowserPerTestStrategyExtension;
import io.quarkus.test.common.http.TestHTTPResource;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({BrowserPerTestStrategyExtension.class})
public abstract class AbstractTest {

    private static final boolean isMacOS =
            System.getProperty("os.name").toLowerCase().contains("mac");

    @TestHTTPResource()
    private String baseURL;

    @BeforeEach
    void setup() {
        if (isMacOS) {
            Configuration.headless = false;
            Configuration.browser = "safari";
        } else {
            Configuration.headless = runHeadless();
            System.setProperty("chromeoptions.args", "--remote-allow-origins=*");
        }
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
        waitForDevServer();
        selector.get().shouldBe(Condition.visible, Duration.ofSeconds(10));
        // There should not be typescript errors
        // $("vite-plugin-checker-error-overlay").shouldNot(Condition.exist);
        $(Selectors.shadowCss("div.dev-tools.error", "vaadin-dev-tools")).shouldNot(Condition.exist);
        $(Selectors.shadowCss("main", "vite-plugin-checker-error-overlay")).shouldNot(Condition.exist);
    }

    protected void waitForDevServer() {
        Selenide.Wait()
                .withTimeout(Duration.ofMinutes(20))
                .until(d -> !Boolean.TRUE.equals(Selenide.executeJavaScript(
                        "return window.Vaadin && window.Vaadin.Flow && window.Vaadin.Flow.devServerIsNotLoaded;")));
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
