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
        openAndWait(() -> $("div.auto-crud"));

        SelenideElement grid =
                $("div.auto-crud div.auto-crud-main > vaadin-grid").shouldBe(Condition.visible);

        assertThat(collectColumnTexts(grid, 1, TestData.RENDERED_ITEMS)).containsExactlyElementsOf(TestData.NAMES_ASC);

        SelenideElement form = $("div.auto-crud div.auto-form").shouldBe(Condition.visible);
        form.$("vaadin-text-field[name=name]").shouldBe(VaadinConditions.disabled);
        form.$("vaadin-text-field[name=surname]").shouldBe(VaadinConditions.disabled);
        form.$("div.auto-form-toolbar vaadin-button[theme=primary]").shouldBe(VaadinConditions.disabled);
    }

    @Test
    void autocrud_newUser_userSaved() {
        openAndWait(() -> $("div.auto-crud"));

        SelenideElement newButton =
                $("div.auto-crud-toolbar vaadin-button[theme=primary]").shouldNotBe(VaadinConditions.disabled);
        newButton.click();

        SelenideElement form = $("div.auto-crud div.auto-form").shouldBe(Condition.visible);
        SelenideElement nameField = form.$("vaadin-text-field[name=name]").shouldNotBe(VaadinConditions.disabled);
        SelenideElement surnameField = form.$("vaadin-text-field[name=surname]").shouldNotBe(VaadinConditions.disabled);

        String name = "Publio Cornelio";
        String surname = "Scipione";

        nameField.setValue(name);
        surnameField.setValue(surname);

        form.$("div.auto-form-toolbar vaadin-button[theme=primary]")
                .shouldNotBe(VaadinConditions.disabled)
                .click();

        SelenideElement grid =
                $("div.auto-crud div.auto-crud-main > vaadin-grid").shouldBe(Condition.visible);
        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/vaadin-text-field/input"));
        nameFilter.setValue(name);
        getCell(grid, 1, 1).shouldHave(Condition.text(name));
        getCell(grid, 1, 2).shouldHave(Condition.text(surname));
    }

    @Test
    void selectUser_editAndDiscard() {
        openAndWait(() -> $("div.auto-crud"));

        SelenideElement grid =
                $("div.auto-crud div.auto-crud-main > vaadin-grid").shouldBe(Condition.visible);

        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_48.name());

        getCell(grid, 1, 1).click();

        SelenideElement form = $("div.auto-crud div.auto-form").shouldBe(Condition.visible);
        SelenideElement surnameField =
                form.$("vaadin-text-field[name=surname]").shouldNotHave(VaadinConditions.disabled);
        surnameField.shouldHave(Condition.value(TestData.USER_48.surname()));
        surnameField.setValue("Simpson");

        SelenideElement discardButton =
                form.$("div.auto-form-toolbar vaadin-button[theme=tertiary]").shouldNotBe(VaadinConditions.disabled);
        discardButton.click();

        getCell(grid, 1, 2).shouldHave(Condition.text(TestData.USER_48.surname()));

        surnameField.shouldHave(Condition.value(TestData.USER_48.surname()));

        discardButton.shouldNotBe(Condition.visible);
    }

    @Test
    void selectUser_editAndSubmit() {
        openAndWait(() -> $("div.auto-crud"));

        SelenideElement grid =
                $("div.auto-crud div.auto-crud-main > vaadin-grid").shouldBe(Condition.visible);

        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_74.name());

        getCell(grid, 1, 1).click();

        SelenideElement form = $("div.auto-crud div.auto-form").shouldBe(Condition.visible);
        SelenideElement surnameField =
                form.$("vaadin-text-field[name=surname]").shouldNotHave(VaadinConditions.disabled);
        surnameField.shouldHave(Condition.value(TestData.USER_74.surname()));
        surnameField.setValue("Simpson");

        form.$("div.auto-form-toolbar vaadin-button[theme=primary]")
                .shouldNotBe(VaadinConditions.disabled)
                .click();

        getCell(grid, 1, 2).shouldHave(Condition.text("Simpson"));

        surnameField.shouldHave(Condition.value("Simpson"));

        form.$("div.auto-form-toolbar vaadin-button[theme=primary]").shouldBe(VaadinConditions.disabled);
    }

    @Test
    void selectUser_delete() {
        openAndWait(() -> $("div.auto-crud"));

        SelenideElement grid =
                $("div.auto-crud div.auto-crud-main > vaadin-grid").shouldBe(Condition.visible);
        SelenideElement nameFilter = grid.$(byXpath("./vaadin-grid-cell-content[3]/vaadin-text-field/input"));
        nameFilter.setValue(TestData.USER_54.name());

        getCell(grid, 1, 1).click();

        SelenideElement form = $("div.auto-crud div.auto-form").shouldBe(Condition.visible);
        form.$("div.auto-form-toolbar vaadin-button.auto-form-delete-button")
                .shouldBe(Condition.visible)
                .shouldNotBe(VaadinConditions.disabled)
                .click();

        SelenideElement confirmDialog = $("vaadin-confirm-dialog-overlay").shouldBe(Condition.visible);
        confirmDialog.$("vaadin-button[slot=confirm-button]").click();

        confirmDialog.shouldNotBe(Condition.visible);

        assertThat(collectColumnTexts(grid, 1, 0)).isEmpty();
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
        String slotName = grid.$$(shadowDeepCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ") slot"))
                .get(row - 1)
                .getAttribute("name");
        return grid.$("vaadin-grid-cell-content[slot=" + slotName + "]").should(Condition.exist);
    }
}
