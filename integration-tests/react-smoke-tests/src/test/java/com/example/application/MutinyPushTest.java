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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.codeborne.selenide.Condition;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class MutinyPushTest extends AbstractTest {

    public static final String COMPLETE_MESSAGE = "Bye. Thanks.";

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "push-mutiny";
    }

    @Test
    void publicEndpoint_anonymousUser_changesPushed() {
        openAndWait(() -> $("div#push-view"));

        $("vaadin-button#public").click();

        AtomicReference<String> previousContents = new AtomicReference<>();
        await().pollInSameThread().during(5, TimeUnit.SECONDS).untilAsserted(() -> changesPushed(previousContents));

        $("vaadin-button#stop").click();

        $("div#push-contents").shouldNot(visible);
    }

    @Test
    void publicEndpoint_anonymousUser_subscriptionAutoClose() {
        openAndWait(() -> $("div#push-view"));

        $("vaadin-button#public-limit").click();

        AtomicReference<String> previousContents = new AtomicReference<>();
        await().pollInSameThread().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> changesPushed(previousContents));

        await().pollInSameThread()
                .until(() -> COMPLETE_MESSAGE.equals(
                        $("div#push-contents").shouldBe(visible).getText()));
    }

    @Test
    void protectedEndpoint_anonymousUser_subscriptionFails() {
        openAndWait(() -> $("div#push-view"));

        $("vaadin-button#protected").click();

        $("div#push-contents")
                .shouldBe(visible)
                .shouldHave(Condition.text("Something failed. Maybe you are not authorized?"));
    }

    // Checks that the current shown message is different from the previous one
    // to ensures new content is pushed from the server
    private static void changesPushed(AtomicReference<String> previousContents) {
        String contents = $("div#push-contents").shouldBe(visible).getText();
        if (!COMPLETE_MESSAGE.equals(contents)) {
            Assertions.assertThat(contents)
                    .startsWith("PUBLIC:")
                    .contains("Anonymous")
                    .isNotEqualTo(previousContents);
        }
        previousContents.set(contents);
    }
}
