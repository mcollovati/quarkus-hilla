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

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.WebElementCondition;
import com.codeborne.selenide.conditions.CustomMatch;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Selenide.$;

public final class VaadinConditions {

    public static final WebElementCondition disabled = Condition.attribute("disabled");

    public static WebElementCondition sideNavItem(String href) {
        return new CustomMatch(
                "SideNavItem[" + href + "]",
                element -> "vaadin-side-nav-item".equals(element.getTagName())
                        && $(element.getShadowRoot().findElement(By.cssSelector("a")))
                                .exists()
                        && $(element.getShadowRoot().findElement(By.cssSelector("a")))
                                .has(Condition.and(
                                        "Visible SideNavItem[" + href + "]",
                                        Condition.domAttribute("href", href),
                                        Condition.visible)));
    }
}
