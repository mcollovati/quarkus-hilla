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
package com.github.mcollovati.quarkus.hilla.deployment.devui;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.IsTest;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.devui.spi.page.RawDataPageBuilder;

/*
  // Test-only processor to produce a Dev UI CardPageBuildItem with build-time data.
*/
public class DevUITestProcessor {

    @BuildStep(onlyIf = IsTest.class)
    public void addTestCard(BuildProducer<CardPageBuildItem> cardPageProducer, EndpointBuildItem endpointBuildItem) {
        CardPageBuildItem card = new CardPageBuildItem();
        RawDataPageBuilder page = Page.rawDataPageBuilder("Endpoints")
                .title("Browser callables (test)")
                .staticLabel(String.valueOf(endpointBuildItem.getEndpointMethodCount()))
                .buildTimeDataKey("testEndpoints");
        card.addPage(page);
        card.addBuildTimeData("testEndpoints", endpointBuildItem.getEndpoints());
        cardPageProducer.produce(card);
    }
}
