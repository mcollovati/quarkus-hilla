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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.devui.spi.DevUIContent;
import io.quarkus.devui.spi.buildtime.StaticContentBuildItem;
import io.vertx.core.json.jackson.DatabindCodec;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.slf4j.LoggerFactory;

public class QuarkusHillaDevUICommonsProcessor {

    private static final String NAMESPACE = "quarkus-hilla-commons";

    private static final DotName SIGNALS_HANDLER =
            DotName.createSimple("com.vaadin.hilla.signals.handler.SignalsHandler");

    private static final String FILE_PATH = "dev-ui/qwc-quarkus-hilla-browser-callables.js";

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
                .sorted(Comparator.comparing(ClassInfo::name))
                .map(e -> EndpointInfo.from(e, combinedIndexBuildItem.getComputingIndex()))
                .toList();
        return new EndpointBuildItem(endpoints);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void createSharedWebComponent(
            BuildProducer<StaticContentBuildItem> staticContentProducer, EndpointBuildItem endpointBuildItem) {

        String webComponent = getWebComponentFromResource();
        String endpoints = toJsonArrayString(endpointBuildItem.getEndpoints());

        staticContentProducer.produce(new StaticContentBuildItem(
                NAMESPACE,
                List.of(
                        DevUIContent.builder()
                                .fileName("qwc-quarkus-hilla-browser-callables.js")
                                .template(webComponent.getBytes(StandardCharsets.UTF_8))
                                .build(),
                        DevUIContent.builder()
                                .fileName("quarkus-hilla-application-data.js")
                                .addData("buildTimeData", Map.of("hillaEndpoints", endpoints))
                                .template(
                                        """
                                                {#for d in buildTimeData}\s
                                                export const {d.key} = {d.value};
                                                {/for}
                                                """
                                                .getBytes(StandardCharsets.UTF_8))
                                .build())));
    }

    private String getWebComponentFromResource() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(FILE_PATH)) {
            if (is == null) {
                throw new IOException("Could not find template: " + FILE_PATH);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("{", "\\{");
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to generate qwc-quarkus-hilla-browser-callables shared web-component", e);
        }
    }

    private String toJsonArrayString(List<EndpointInfo> endpointInfos) {
        var mapper = DatabindCodec.mapper().writerWithDefaultPrettyPrinter();
        try {
            return mapper.writeValueAsString(endpointInfos);
        } catch (JsonProcessingException e) {
            LoggerFactory.getLogger(getClass()).error("Failed to serialize endpoints for Dev UI page", e);
            return "[]";
        }
    }
}
