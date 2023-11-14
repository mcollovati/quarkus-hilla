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
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;
import com.github.mcollovati.quarkus.testing.VaadinConditions;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Selenide.$;

@QuarkusTest
@TestTransaction
class AutoFormTest extends AbstractTest {

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "auto-form";
    }

    @Test
    void newUser_userSaved() {
        openAndWait(() -> $("div.auto-form"));

        String name = "Gaio Giulio";
        String surname = "Cesare";
        $("vaadin-text-field[name=name]").setValue(name);
        $("vaadin-text-field[name=surname]").setValue(surname);
        $("div.auto-form vaadin-button[theme=primary]").click();

        notificationShown(name, surname, null);
    }

    @Test
    void loadUser_editAndDiscard() {
        TestData.UserData user = TestData.USER_51;

        openAndWait(() -> $("div.auto-form"));

        $("vaadin-button#user" + user.id()).click();

        SelenideElement nameField = $("vaadin-text-field[name=name]");
        nameField.shouldHave(Condition.value(user.name()));
        SelenideElement surnameField = $("vaadin-text-field[name=surname]");
        surnameField.shouldHave(Condition.value(user.surname()));
        SelenideElement submitButton = $("div.auto-form vaadin-button[theme=primary]");
        submitButton.shouldBe(VaadinConditions.disabled);
        SelenideElement discardButton = $("div.auto-form vaadin-button[theme=tertiary]");
        discardButton.shouldNotBe(Condition.visible);

        nameField.setValue("Emanuela");
        submitButton.shouldNotBe(VaadinConditions.disabled);
        discardButton.shouldBe(Condition.visible);

        discardButton.click();

        submitButton.shouldBe(VaadinConditions.disabled);
        discardButton.shouldNotBe(Condition.visible);
        nameField.shouldHave(Condition.value(user.name()));
        surnameField.shouldHave(Condition.value(user.surname()));
    }

    @Test
    void loadUser_editAndSubmit() {
        TestData.UserData user = TestData.USER_48;

        openAndWait(() -> $("div.auto-form"));

        $("vaadin-button#user" + user.id()).click();

        SelenideElement nameField = $("vaadin-text-field[name=name]");
        nameField.shouldHave(Condition.value(user.name()));
        SelenideElement surnameField = $("vaadin-text-field[name=surname]");
        surnameField.shouldHave(Condition.value(user.surname()));
        SelenideElement submitButton = $("div.auto-form vaadin-button[theme=primary]");
        submitButton.shouldBe(VaadinConditions.disabled);
        SelenideElement discardButton = $("div.auto-form vaadin-button[theme=tertiary]");
        discardButton.shouldNotBe(Condition.visible);

        nameField.setValue("Emanuela");
        submitButton.shouldNotBe(VaadinConditions.disabled);
        discardButton.shouldBe(Condition.visible);

        submitButton.click();

        submitButton.shouldBe(VaadinConditions.disabled);
        discardButton.shouldNotBe(Condition.visible);
        nameField.shouldHave(Condition.value("Emanuela"));
        surnameField.shouldHave(Condition.value(user.surname()));

        notificationShown("Emanuela", user.surname(), user.id());
    }

    private void notificationShown(String name, String surname, Integer id) {
        SelenideElement notification = $("vaadin-notification-card");
        notification.shouldHave(text("Saved user " + name + " " + surname));
        notification.shouldHave(Condition.matchText(".*with id " + ((id == null) ? "\\d+" : id) + "$"));
    }
}
