package com.example.application;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

@QuarkusTest
class SmokeTest extends AbstractTest {

    @Test
    void rootPath_viewDisplayed() {
        openAndWait(() -> $("hello-world-view"));

        $("vaadin-text-field").shouldBe(visible);
        SelenideElement button = $$("vaadin-button")
                .filter(Condition.text("Say Hello")).first().shouldBe(visible);
    }

    @Test
    void publicEndpoint_invocationSucceeded() {
        openAndWait(() -> $("hello-world-view"));

        SelenideElement textField = $("vaadin-text-field").shouldBe(visible);
        SelenideElement button = $$("vaadin-button")
                .filter(Condition.text("Say Hello")).first().shouldBe(visible);

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
}
