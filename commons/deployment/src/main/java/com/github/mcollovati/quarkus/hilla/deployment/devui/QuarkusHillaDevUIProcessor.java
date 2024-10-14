/*
 * Copyright 2024 Marco Collovati, Dario Götze
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

import java.util.stream.Stream;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

public class QuarkusHillaDevUIProcessor {

    private static final DotName SIGNALS_HANDLER =
            DotName.createSimple("com.vaadin.hilla.signals.handler.SignalsHandler");

    @BuildStep(onlyIf = IsDevelopment.class)
    public EndpointBuildItem collectEndpoints(CombinedIndexBuildItem combinedIndexBuildItem) {
        final var endpointAnnotated =
                combinedIndexBuildItem.getComputingIndex().getAnnotations(EndpointInfo.ENDPOINT_ANNOTATION);
        final var browserCallableAnnotated =
                combinedIndexBuildItem.getComputingIndex().getAnnotations(EndpointInfo.BROWSER_CALLABLE_ANNOTATION);
        final var endpoints = Stream.concat(endpointAnnotated.stream(), browserCallableAnnotated.stream())
                .map(AnnotationInstance::target)
                .filter(target -> target.kind().equals(AnnotationTarget.Kind.CLASS))
                .map(AnnotationTarget::asClass)
                .filter(c -> !SIGNALS_HANDLER.equals(c.name()))
                .map(EndpointInfo::from)
                .toList();
        return new EndpointBuildItem(endpoints);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem pages(EndpointBuildItem endpointBuildItems) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();
        cardPageBuildItem.addBuildTimeData("hillaEndpoints", endpointBuildItems.getEndpoints());
        cardPageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Endpoints")
                .icon("font-awesome-solid:table-list")
                .componentLink("qwc-quarkus-hilla-endpoints.js")
                .staticLabel(String.valueOf(endpointBuildItems.getEndpoints().stream()
                        .mapToInt(a -> a.children().size())
                        .sum())));
        return cardPageBuildItem;
    }
}
