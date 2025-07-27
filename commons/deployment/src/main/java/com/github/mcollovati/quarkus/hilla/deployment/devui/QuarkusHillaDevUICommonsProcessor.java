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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.devui.spi.DevUIContent;
import io.quarkus.devui.spi.buildtime.StaticContentBuildItem;
import io.quarkus.maven.dependency.GACT;
import io.vertx.core.json.jackson.DatabindCodec;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

public class QuarkusHillaDevUICommonsProcessor {

    private static final GACT UI_JAR =
            new GACT("com.github.mcollovati", "quarkus-hilla-commons-deployment", null, "jar");
    private static final String NAMESPACE = UI_JAR.getGroupId() + "." + UI_JAR.getArtifactId();
    private static final String DEV_UI = "dev-ui";

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
                .sorted(Comparator.comparing(ClassInfo::name))
                .map(e -> EndpointInfo.from(e, combinedIndexBuildItem.getComputingIndex()))
                .toList();
        return new EndpointBuildItem(endpoints);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void createShared(BuildProducer<StaticContentBuildItem> producer, EndpointBuildItem endpointBuildItem)
            throws IOException {

        ObjectWriter objectWriter = DatabindCodec.mapper().writerWithDefaultPrettyPrinter();
        var hillaEndpointsJS = endpointBuildItem.getEndpoints().stream()
                .map(obj -> {
                    try {
                        return objectWriter.writeValueAsString(obj);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining(",\n", "export const hillaEndpoints = [\n", "];"));
        InputStream callablesJsStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("/dev-ui/qwc-quarkus-hilla-browser-callables.js");
        String js = new String(callablesJsStream.readAllBytes(), StandardCharsets.UTF_8).replace("{", "\\{");

        producer.produce(new StaticContentBuildItem(
                NAMESPACE,
                List.of(
                        DevUIContent.builder()
                                .fileName("build-time-data.js")
                                .template(hillaEndpointsJS.getBytes(StandardCharsets.UTF_8))
                                .build(),
                        DevUIContent.builder()
                                .fileName("qwc-quarkus-hilla-browser-callables.js")
                                .template(js.getBytes(StandardCharsets.UTF_8))
                                .build())));
    }
}
