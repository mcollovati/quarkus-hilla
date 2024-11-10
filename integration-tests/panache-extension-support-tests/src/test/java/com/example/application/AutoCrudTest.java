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

import java.util.List;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;
import com.github.mcollovati.quarkus.testing.VaadinConditions;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.shadowDeepCss;
import static com.codeborne.selenide.Selenide.$;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestTransaction
class AutoCrudTest extends AbstractTest {

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "auto-crud";
    }

    @Test
    void autocrud_gridAndFormDisplayed() {
        openAndWait(AutoCrudTest::autoCrud);

        SelenideElement grid = autoCrudGrid().shouldBe(Condition.visible);

        assertThat(collectColumnTexts(grid, 1, TestData.RENDERED_ITEMS)).containsExactlyElementsOf(TestData.NAMES_ASC);

        SelenideElement form = autoCrudForm().shouldBe(Condition.visible);
        formField(form, "name").shouldBe(VaadinConditions.disabled);
        formField(form, "surname").shouldBe(VaadinConditions.disabled);
        submitButton(form).shouldBe(VaadinConditions.disabled);
    }

    @Test
    void autocrud_newUser_userSaved() {
        openAndWait(AutoCrudTest::autoCrud);

        SelenideElement newButton = newButton().shouldNotBe(VaadinConditions.disabled);
        newButton.click();

        SelenideElement form = autoCrudForm().shouldBe(Condition.visible);
        SelenideElement nameField = formField(form, "name").shouldNotBe(VaadinConditions.disabled);
        SelenideElement surnameField = formField(form, "surname").shouldNotBe(VaadinConditions.disabled);

        String name = "Publio Cornelio";
        String surname = "Scipione";

        nameField.setValue(name);
        surnameField.setValue(surname);

        submitButton(form).click();

        SelenideElement grid = autoCrudGrid().shouldBe(Condition.visible);
        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/div/vaadin-text-field/input"));
        nameFilter.setValue(name);
        getCell(grid, 1, 1).shouldHave(Condition.text(name));
        getCell(grid, 1, 2).shouldHave(Condition.text(surname));
    }

    @Test
    void selectUser_editAndDiscard() {
        openAndWait(AutoCrudTest::autoCrud);

        SelenideElement grid = autoCrudGrid().shouldBe(Condition.visible);

        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/div/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_48.name());

        getCell(grid, 1, 2)
                .shouldHave(Condition.text(TestData.USER_48.surname()))
                .click();

        SelenideElement form = autoCrudForm().shouldBe(Condition.visible);
        SelenideElement surnameField = formField(form, "surname").shouldNotHave(VaadinConditions.disabled);
        surnameField.shouldHave(Condition.value(TestData.USER_48.surname()));
        surnameField.setValue("Simpson");

        SelenideElement discardButton = discardButton(form).shouldNotBe(VaadinConditions.disabled);
        discardButton.click();

        getCell(grid, 1, 2).shouldHave(Condition.text(TestData.USER_48.surname()));

        surnameField.shouldHave(Condition.value(TestData.USER_48.surname()));

        discardButton.shouldNotBe(Condition.visible);
    }

    @Test
    void selectUser_editAndSubmit() {
        openAndWait(AutoCrudTest::autoCrud);

        SelenideElement grid = autoCrudGrid().shouldBe(Condition.visible);

        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/div/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_74.name());

        getCell(grid, 1, 2)
                .shouldHave(Condition.text(TestData.USER_74.surname()))
                .click();

        SelenideElement form = autoCrudForm().shouldBe(Condition.visible);
        SelenideElement surnameField = formField(form, "surname").shouldNotHave(VaadinConditions.disabled);
        surnameField.shouldHave(Condition.value(TestData.USER_74.surname()));
        surnameField.setValue("Simpson");

        SelenideElement submitButton = submitButton(form);
        submitButton.shouldNotBe(VaadinConditions.disabled).click();

        getCell(grid, 1, 2).shouldHave(Condition.text("Simpson"));

        surnameField.shouldHave(Condition.value("Simpson"));

        submitButton.shouldBe(VaadinConditions.disabled);
    }

    @Test
    void selectUser_delete() {
        openAndWait(AutoCrudTest::autoCrud);

        SelenideElement grid = autoCrudGrid().shouldBe(Condition.visible);
        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/div/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_54.name());

        getCell(grid, 1, 2)
                .shouldHave(Condition.text(TestData.USER_54.surname()))
                .click();

        SelenideElement form = autoCrudForm().shouldBe(Condition.visible);
        deleteButton(form)
                .shouldBe(Condition.visible)
                .shouldNotBe(VaadinConditions.disabled)
                .click();

        SelenideElement confirmDialog = $("vaadin-confirm-dialog-overlay").shouldBe(Condition.visible);
        SelenideElement confirmButton = confirmDialog.$("vaadin-button[slot=confirm-button]");
        confirmButton.click();

        confirmDialog.shouldNotBe(Condition.visible);

        assertThat(collectColumnTexts(grid, 1, 0)).isEmpty();
    }

    private static SelenideElement autoCrud() {
        return $("div.auto-crud");
    }

    private static SelenideElement autoCrudGrid() {
        return $("div.auto-crud div.auto-crud-main > vaadin-grid");
    }

    private static SelenideElement autoCrudForm() {
        return $("div.auto-crud div.auto-form");
    }

    private static SelenideElement newButton() {
        return $("div.auto-crud-toolbar vaadin-button[theme=primary]");
    }

    private static SelenideElement formField(SelenideElement form, String fieldName) {
        return form.$("vaadin-text-field[name=" + fieldName + "]");
    }

    private static SelenideElement discardButton(SelenideElement form) {
        return form.$("div.auto-form-toolbar vaadin-button[theme=tertiary]");
    }

    private static SelenideElement deleteButton(SelenideElement form) {
        return form.$("div.auto-form-toolbar vaadin-button.auto-form-delete-button");
    }

    private static SelenideElement submitButton(SelenideElement form) {
        return form.$("div.auto-form-toolbar vaadin-button[theme=primary]");
    }

    private static List<String> collectColumnTexts(SelenideElement grid, int column, int expectedSize) {
        return grid
                .$$(shadowDeepCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ")"))
                .filter(Condition.visible)
                .shouldHave(size(expectedSize))
                .asFixedIterable()
                .stream()
                .map(el -> $(el).text())
                .toList();
    }

    private static SelenideElement getCell(SelenideElement grid, int row, int column) {
        String slotName = grid.$$(shadowDeepCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ")"))
                .filter(Condition.visible)
                .get(row - 1)
                .$("slot")
                .getAttribute("name");
        return grid.$("vaadin-grid-cell-content[slot=" + slotName + "]").should(Condition.exist);
    }
}
