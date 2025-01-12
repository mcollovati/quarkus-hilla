/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.devui;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

import com.github.mcollovati.quarkus.hilla.deployment.devui.EndpointBuildItem;

public class QuarkusHillaDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void pages(BuildProducer<CardPageBuildItem> cardPageProducer, EndpointBuildItem endpointBuildItem) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();
        final var page = Page.webComponentPageBuilder()
                .title("Browser callables")
                .icon("font-awesome-solid:table-list")
                .componentLink("qwc-quarkus-hilla-react.js")
                .staticLabel(String.valueOf(endpointBuildItem.getEndpointMethodCount()));
        cardPageBuildItem.addPage(page);
        cardPageProducer.produce(cardPageBuildItem);
    }
}
