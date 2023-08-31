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
package com.github.mcollovati.quarkus.hilla.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.undertow.deployment.ServletDeploymentManagerBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;
import io.quarkus.websockets.client.deployment.ServerWebSocketContainerBuildItem;
import io.quarkus.websockets.client.deployment.WebSocketDeploymentInfoBuildItem;

import com.github.mcollovati.quarkus.hilla.EnableWebsockets;
import com.github.mcollovati.quarkus.hilla.WebsocketHttpSessionAttachRecorder;

/**
 * A Quarkus processor that configure Vaadin minimal requirements when the Vaadin Quarkus extension is not available.
 *
 * Most of the code is copy/pasted from {@code com.vaadin.quarkus.deployment.VaadinQuarkusProcessor}.
 */
class QuarkusHillaStandAloneProcessor {

    @BuildStep
    void indexWebsocketEnablerClass(
            QuarkusHillaEnvironmentBuildItem quarkusHillaEnv,
            BuildProducer<AdditionalIndexedClassesBuildItem> producer) {
        if (!quarkusHillaEnv.isHybrid()) {
            producer.produce(new AdditionalIndexedClassesBuildItem(EnableWebsockets.class.getName()));
        }
    }

    // The extension needs to exclude some resources from Hilla dependencies (exclusions configured in the POM file),
    // and this causes the index to be rebuilt at runtime for those artifacts.
    // However, the hilla-jandex artifact does not contain classes to scan, so we define an additional marker
    // to instruct Quarkus to index required Vaadin artifacts.
    // This is not required in hybrid mode, since the vaadin-quarkus-extension will provide a full jandex index
    // for Vaadin artifacts.
    @BuildStep
    void addMarkersForHillaJars(
            QuarkusHillaEnvironmentBuildItem quarkusHillaEnv,
            BuildProducer<AdditionalApplicationArchiveMarkerBuildItem> producer) {
        if (!quarkusHillaEnv.isHybrid()) {
            producer.produce(new AdditionalApplicationArchiveMarkerBuildItem("com/vaadin"));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configurePush(
            QuarkusHillaEnvironmentBuildItem quarkusHillaEnv,
            ServletDeploymentManagerBuildItem deployment,
            WebSocketDeploymentInfoBuildItem webSocketDeploymentInfoBuildItem,
            ServerWebSocketContainerBuildItem serverWebSocketContainerBuildItem,
            BuildProducer<FilterBuildItem> filterProd,
            WebsocketHttpSessionAttachRecorder recorder) {

        if (!quarkusHillaEnv.isHybrid() && webSocketDeploymentInfoBuildItem != null) {
            filterProd.produce(new FilterBuildItem(
                    recorder.createWebSocketHandler(
                            webSocketDeploymentInfoBuildItem.getInfo(),
                            serverWebSocketContainerBuildItem.getContainer(),
                            deployment.getDeploymentManager()),
                    120));
        }
    }
}
