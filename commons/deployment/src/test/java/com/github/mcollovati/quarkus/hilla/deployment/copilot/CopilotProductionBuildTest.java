/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment.copilot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import org.junit.jupiter.api.Test;

import com.github.mcollovati.quarkus.hilla.deployment.asm.OffendingMethodCallsReplacer;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotProductionBuildTest {

    @Test
    void productionBuild_doesNotRegisterCopilotTransformers() {
        List<BytecodeTransformerBuildItem> transformers = new ArrayList<>();

        OffendingMethodCallsReplacer.addClassVisitors(transformers::add);

        assertThat(transformers)
                .extracting(BytecodeTransformerBuildItem::getClassToTransform)
                .doesNotContain(
                        "com.vaadin.copilot.SpringBridge", "com.vaadin.copilot.customcomponent.CustomComponents");
    }

    @Test
    void copilotBuildSteps_runOnlyInDevelopmentMode() {
        assertThat(copilotBuildStep("addMarkersForCopilotJars").onlyIf()).containsExactly(IsDevelopment.class);
        assertThat(copilotBuildStep("generateCopilotApplicationMetadata").onlyIf())
                .containsExactly(IsDevelopment.class);
        assertThat(copilotBuildStep("preserveCopilotFlowServiceBeans").onlyIf()).containsExactly(IsDevelopment.class);
        assertThat(copilotBuildStep("replaceCopilotMethodCalls").onlyIf()).containsExactly(IsDevelopment.class);
    }

    @Test
    void copilotBuildStep_registersCopilotTransformers() {
        List<BytecodeTransformerBuildItem> transformers = new ArrayList<>();

        OffendingMethodCallsReplacer.addCopilotClassVisitors(transformers::add);

        assertThat(transformers)
                .extracting(BytecodeTransformerBuildItem::getClassToTransform)
                .containsExactly(
                        "com.vaadin.copilot.SpringBridge", "com.vaadin.copilot.customcomponent.CustomComponents");
    }

    private static BuildStep copilotBuildStep(String methodName) {
        try {
            Class<?> processor =
                    Class.forName("com.github.mcollovati.quarkus.hilla.deployment.QuarkusHillaExtensionProcessor");
            Method method = Arrays.stream(processor.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            return method.getAnnotation(BuildStep.class);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
