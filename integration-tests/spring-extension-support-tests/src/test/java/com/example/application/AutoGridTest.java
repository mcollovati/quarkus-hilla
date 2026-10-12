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

import com.codeborne.selenide.CollectionCondition;
import com.codeborne.selenide.Condition;
import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import io.quarkus.test.junit.QuarkusTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.shadowCss;
import static com.codeborne.selenide.Selectors.shadowDeepCss;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.Wait;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AutoGridTest extends AbstractTest {

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "auto-grid";
    }

    @Test
    void autoGrid_gridIsDisplayed() {
        openAndWait(() -> $("vaadin-grid"));
        List<String> items = collectColumnTexts(1);
        assertThat(items).containsExactlyElementsOf(TestData.NAMES_ASC.subList(0, items.size()));

        SelenideElement filterRows =
                $$(shadowCss("thead#header tr[part~=\"row\"]", "vaadin-grid")).last();
        filterRows
                .$$(shadowDeepCss("vaadin-text-field[placeholder=\"Filter...\"]"))
                .shouldHave(size(2));

        $$(shadowCss("tbody#items tr[part~=\"row\"]", "vaadin-grid")).shouldHave(size(items.size()));
    }

    @Test
    void autoGrid_filter() {
        openAndWait(() -> $("vaadin-grid"));
        SelenideElement filter = $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[3]/div/vaadin-text-field/input"));

        List<String> items = collectColumnTexts(1);
        assertThat(items).containsExactlyElementsOf(TestData.NAMES_ASC.subList(0, items.size()));

        unsort();

        filter.setValue("er");

        columnShouldHaveTexts(
                1, "Homer", "Peter", "Christopher", "Avery", "Harper", "Cameron", "Carter", "Tyler", "Parker");

        filter.setValue("Ho");
        columnShouldHaveTexts(1, "Homer", "Nicholas");

        filter.setValue("");
        items = collectColumnCells(1)
                .shouldHave(CollectionCondition.sizeGreaterThan(2))
                .texts();
        assertThat(items).containsExactlyElementsOf(TestData.NAMES_UNSORTED.subList(0, items.size()));
    }

    @Test
    void autoGrid_sort() {
        openAndWait(() -> $("vaadin-grid"));
        SelenideElement nameSorter = unsort();
        collectColumnCells(1).shouldHave(CollectionCondition.sizeGreaterThan(2));
        int renderedRows = Math.max(3, collectColumnTexts(1).size());
        awaitColumnTexts(1, TestData.NAMES_UNSORTED, renderedRows);

        // Sort by name ascending
        nameSorter.click();
        assertThat(nameSorter.getAttribute("direction")).isEqualTo("asc");
        awaitColumnTexts(1, TestData.NAMES_ASC, renderedRows);

        // Sort by name descending
        nameSorter.click();
        assertThat(nameSorter.getAttribute("direction")).isEqualTo("desc");
        awaitColumnTexts(1, TestData.NAMES_DESC, renderedRows);

        // Unsorted
        nameSorter.click();
        assertThat(nameSorter.getAttribute("direction")).isNull();
        awaitColumnTexts(1, TestData.NAMES_UNSORTED, renderedRows);
    }

    private static @NotNull SelenideElement unsort() {
        SelenideElement nameSorter = $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[1]/vaadin-grid-sorter"));
        String direction = nameSorter.getAttribute("direction");
        while (direction != null) {
            nameSorter.click();
            direction = nameSorter.getAttribute("direction");
        }
        return nameSorter;
    }

    private static ElementsCollection collectColumnCells(int column) {
        return $$($$(shadowCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ") slot", "vaadin-grid"))
                        .attributes("name")
                        .stream()
                        .map(id -> $("vaadin-grid vaadin-grid-cell-content[slot='" + id + "']"))
                        .toList())
                .filter(Condition.visible);
    }

    private static List<String> collectColumnTexts(int column) {
        return collectColumnCells(column).texts();
    }

    private static List<String> awaitColumnTexts(int column, List<String> expected, int minimumRows) {
        return Wait().until(driver -> {
            List<String> texts = collectColumnTexts(column);
            if (texts.size() < Math.max(1, minimumRows)
                    || texts.size() > expected.size()
                    || !texts.equals(expected.subList(0, texts.size()))) {
                return null;
            }
            return texts;
        });
    }

    private static void columnShouldHaveTexts(int column, String... texts) {
        collectColumnCells(column).shouldHave(CollectionCondition.textsInAnyOrder(texts));
    }
}
