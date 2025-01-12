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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.devui.deployment.BuildTimeConstBuildItem;
import io.quarkus.devui.deployment.DevUIWebJarBuildItem;
import io.quarkus.maven.dependency.GACT;
import io.quarkus.vertx.http.deployment.webjar.WebJarBuildItem;
import io.quarkus.vertx.http.deployment.webjar.WebJarResourcesFilter;
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
    void createShared(
            BuildProducer<WebJarBuildItem> webJarBuildProducer,
            BuildProducer<DevUIWebJarBuildItem> devUIWebJarProducer,
            BuildProducer<BuildTimeConstBuildItem> buildTimeConstProducer,
            EndpointBuildItem endpointBuildItem) {

        final Map<String, Object> buildTimeData = new HashMap<>();
        buildTimeData.put("hillaEndpoints", endpointBuildItem.getEndpoints());
        buildTimeConstProducer.produce(new BuildTimeConstBuildItem(NAMESPACE, buildTimeData));

        String buildTimeDataImport = NAMESPACE + "-data";

        webJarBuildProducer.produce(WebJarBuildItem.builder()
                .artifactKey(UI_JAR)
                .root(DEV_UI + "/")
                .filter((fileName, file) -> {
                    if (fileName.endsWith(".js")) {
                        String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
                        content = content.replaceAll("build-time-data", buildTimeDataImport);
                        return new WebJarResourcesFilter.FilterResult(
                                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true);
                    }
                    return new WebJarResourcesFilter.FilterResult(file, false);
                })
                .build());
        devUIWebJarProducer.produce(new DevUIWebJarBuildItem(UI_JAR, DEV_UI));
    }
}
