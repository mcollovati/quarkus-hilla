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

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.Configuration;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.Selectors;
import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.junit5.BrowserPerTestStrategyExtension;
import io.quarkus.test.common.http.TestHTTPResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.Wait;

@ExtendWith({BrowserPerTestStrategyExtension.class})
public abstract class AbstractTest {

    private static final boolean isMacOS =
            System.getProperty("os.name").toLowerCase().contains("mac");

    @TestHTTPResource()
    private String baseURL;

    @BeforeEach
    void setup(TestInfo info) {

        System.setProperty(
                "webdriver.chrome.logfile",
                "/tmp/mylogs/chromedriver-"
                        + info.getDisplayName().replace(" ", "_").replaceAll("[()]", "")
                        + ".log");
        System.setProperty("webdriver.chrome.verboseLogging", "true");

        if (isMacOS) {
            Configuration.headless = false;
            Configuration.browser = "safari";
        } else {
            Configuration.headless = runHeadless();
            System.setProperty("chromeoptions.args", "--remote-allow-origins=*,--no-sandbox,--disable-gpu");
        }
        Configuration.fastSetValue = true;

        // Workaround for chromedriver timeouts in selenium 4.37
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
        Configuration.remoteReadTimeout = 10_000;
        Configuration.pageLoadStrategy = "eager";
        Configuration.browserCapabilities.setCapability(
                "goog:loggingPrefs", Map.of("browser", "ALL", "driver", "ALL", "performance", "ALL"));
        Configuration.browserCapabilities.setCapability("webSocketUrl", true);
        // Configuration.browserCapabilities.setCapability("se:cdpEnabled", false);

        // Disable Copilot because currently it slows down the page load
        // because of license checking
        System.setProperty("vaadin.copilot.enable", "false");
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
        openAndWait(url, selector, true);
    }

    protected void openAndWait(String url, Supplier<SelenideElement> selector, boolean checkErrors) {
        Selenide.open(url);
        waitForDevServer();
        selector.get().shouldBe(Condition.visible, Duration.ofSeconds(10));
        if (checkErrors) {
            SelenideElement devToolsError = $(Selectors.shadowCss("div.dev-tools.error", "vaadin-dev-tools"));
            if (devToolsError.is(Condition.visible)) {
                devToolsError.click();
                $$(Selectors.shadowCss("div.message-tray div.message.error", "vaadin-dev-tools"))
                        .shouldBe(CollectionCondition.empty);
            }
            $(Selectors.shadowCss("main", "vite-plugin-checker-error-overlay")).shouldNotBe(Condition.visible);
        }
        // Close Dev Tools messages
        Wait().until(d -> {
            ElementsCollection dismissElements = $$(Selectors.shadowCss("div.dismiss-message", "vaadin-dev-tools"))
                    .filter(Condition.visible);
            int visibleElements = dismissElements.size();
            if (!dismissElements.isEmpty()) {
                dismissElements.first().click();
            }
            return visibleElements <= 1;
        });
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
