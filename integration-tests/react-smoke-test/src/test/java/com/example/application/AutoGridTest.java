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

import java.util.ArrayList;
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

    @Override
    protected String getTestUrl() {
        return getBaseURL() + "auto-grid";
    }

    @Test
    void autoGrid_gridIsDisplayed() {
        openAndWait(() -> $("vaadin-grid"));

        SelenideElement filterRows =
                $$(shadowCss("thead#header tr[part~=\"row\"]", "vaadin-grid")).last();
        filterRows
                .$$(shadowDeepCss("vaadin-text-field[placeholder=\"Filter...\"]"))
                .shouldHave(size(3));

        $$(shadowCss("tbody#items tr[part~=\"row\"]", "vaadin-grid")).shouldHave(size(15));
    }

    @Test
    void autoGrid_sort() {
        openAndWait(() -> $("vaadin-grid"));

        // Sort by name ascending
        $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[5]/vaadin-text-field/input"))
                .setValue("10");

        List<String> filteredTexts = collectColumnTexts(2);
        assertThat(filteredTexts).containsExactlyInAnyOrder("Name 10", "Name 100");
    }

    @Test
    void autoGrid_filter() {
        openAndWait(() -> $("vaadin-grid"));

        // Get texts from 'Name' column
        List<String> texts = collectColumnTexts(2);

        // Sort by name ascending
        $(byXpath("//*/vaadin-grid/vaadin-grid-cell-content[2]/vaadin-grid-sorter"))
                .click();

        List<String> sortedTexts = collectColumnTexts(2);

        List<String> expected = List.of(
                "Name 1",
                "Name 10",
                "Name 100",
                "Name 11",
                "Name 12",
                "Name 13",
                "Name 14",
                "Name 15",
                "Name 16",
                "Name 17",
                "Name 18",
                "Name 19",
                "Name 2",
                "Name 20",
                "Name 21");

        List<String> diff = new ArrayList<>(sortedTexts);
        diff.removeAll(texts);
        assertThat(diff).isNotEmpty();
        assertThat(sortedTexts).containsExactlyElementsOf(expected);
    }

    private static List<String> collectColumnTexts(int column) {
        return $$(shadowCss("tbody#items tr[part~=\"row\"] td:nth-child(" + column + ")", "vaadin-grid"))
                .asFixedIterable()
                .stream()
                .filter(el -> el.is(Condition.visible))
                .map(el -> $(el).text())
                .toList();
    }
}
