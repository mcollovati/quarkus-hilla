package com.github.mcollovati.quarkus.hilla.devui;

import com.github.mcollovati.quarkus.hilla.deployment.devui.EndpointBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class QuarkusHillaDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void pages(
            BuildProducer<CardPageBuildItem> cardPageProducer,
            EndpointBuildItem endpointBuildItem) {
        CardPageBuildItem cardPageBuildItem = new CardPageBuildItem();
        final var page = Page.webComponentPageBuilder()
                .title("Browser callables")
                .icon("font-awesome-solid:table-list")
                .componentLink("qwc-quarkus-hilla.js")
                .staticLabel(String.valueOf(endpointBuildItem.getEndpointMethodCount()));
        cardPageBuildItem.addPage(page);
        cardPageProducer.produce(cardPageBuildItem);
    }
}
