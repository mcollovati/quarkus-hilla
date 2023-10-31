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
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.testing.AbstractTest;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Selectors.byXpath;
import static com.codeborne.selenide.Selectors.shadowCss;
import static com.codeborne.selenide.Selectors.shadowDeepCss;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AutoGridTest extends AbstractTest {

    private static final int ALL_ITEMS = 3;

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "auto-grid";
    }

    @Test
    void autoGrid_gridIsDisplayed() {
        openAndWait(() -> $("vaadin-grid"));
        assertThat(collectColumnTexts(1)).containsExactlyElementsOf(List.of("Homer", "Jason", "Peter"));

        SelenideElement filterRows =
                $$(shadowCss("thead#header tr[part~=\"row\"]", "vaadin-grid")).last();
        filterRows
                .$$(shadowDeepCss("vaadin-text-field[placeholder=\"Filter...\"]"))
                .shouldHave(size(2));

        $$(shadowCss("tbody#items tr[part~=\"row\"]", "vaadin-grid")).shouldHave(size(3));
    }

    @Test
    void autoGrid_filter() throws InterruptedException {
        openAndWait(() -> $("vaadin-grid"));
        SelenideElement filter = $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[3]/vaadin-text-field/input"));

        assertThat(collectColumnTexts(1, ALL_ITEMS)).containsExactlyInAnyOrder("Homer", "Jason", "Peter");

        filter.setValue("er");
        assertThat(collectColumnTexts(1, 2)).containsExactlyInAnyOrder("Homer", "Peter");

        filter.setValue("H");
        assertThat(collectColumnTexts(1, 1)).containsExactlyInAnyOrder("Homer");

        filter.setValue("");
        assertThat(collectColumnTexts(1, ALL_ITEMS)).containsExactlyInAnyOrder("Homer", "Jason", "Peter");
    }

    @Test
    void autoGrid_sort() {
        openAndWait(() -> $("vaadin-grid"));
        assertThat(collectColumnTexts(1)).containsExactlyElementsOf(List.of("Homer", "Jason", "Peter"));

        SelenideElement nameSorter = $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[1]/vaadin-grid-sorter"));

        String direction = nameSorter.getAttribute("direction");
        while (direction != null) {
            nameSorter.click();
            direction = nameSorter.getAttribute("direction");
        }

        // Sort by name ascending
        nameSorter.click();
        assertThat(collectColumnTexts(1)).containsExactlyElementsOf(List.of("Homer", "Jason", "Peter"));

        // Sort by name ascending
        nameSorter.click();
        assertThat(collectColumnTexts(1)).containsExactlyElementsOf(List.of("Peter", "Jason", "Homer"));
    }

    private static List<String> collectColumnTexts(int column) {
        return collectColumnTexts(column, ALL_ITEMS);
    }

    private static List<String> collectColumnTexts(int column, int expectedSize) {
        return $$(shadowCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ")", "vaadin-grid"))
                .filter(Condition.visible)
                .shouldHave(size(expectedSize))
                .asFixedIterable()
                .stream()
                .map(el -> $(el).text())
                .toList();
    }
}
