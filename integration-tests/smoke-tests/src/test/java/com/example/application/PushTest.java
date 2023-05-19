package com.example.application;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.codeborne.selenide.Condition;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class PushTest extends AbstractTest {

    public static final String COMPLETE_MESSAGE = "Bye. Thanks.";

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "push";
    }

    @Test
    void publicEndpoint_anonymousUser_changesPushed() {
        openAndWait(() -> $("push-view"));

        $("vaadin-button#public").click();

        AtomicReference<String> previousContents = new AtomicReference<>();
        await().pollInSameThread()
                .during(5, TimeUnit.SECONDS)
                .untilAsserted(() -> changesPushed(previousContents));

        $("vaadin-button#stop").click();

        $("div#push-contents").shouldNot(visible);
    }

    @Test
    void publicEndpoint_anonymousUser_subscriptionAutoClose() {
        openAndWait(() -> $("push-view"));

        $("vaadin-button#public-limit").click();

        AtomicReference<String> previousContents = new AtomicReference<>();
        await().pollInSameThread()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> changesPushed(previousContents));

        await().pollInSameThread()
                .until(() -> COMPLETE_MESSAGE.equals($("div#push-contents").shouldBe(visible).getText()));
    }

    @Test
    void protectedEndpoint_anonymousUser_subscriptionFails() {
        openAndWait(() -> $("push-view"));

        $("vaadin-button#protected").click();

        $("div#push-contents").shouldBe(visible)
                .shouldHave(Condition.text("Something failed. Maybe you are not authorized?"));
    }

    private static void changesPushed(AtomicReference<String> previousContents) {
        String contents = $("div#push-contents").shouldBe(visible).getText();
        if (!COMPLETE_MESSAGE.equals(contents)) {
            Assertions.assertThat(contents).startsWith("PUBLIC:")
                    .contains("Anonymous")
                    .isNotEqualTo(previousContents);
        }
        previousContents.set(contents);
    }


}
