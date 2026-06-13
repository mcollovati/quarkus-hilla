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
package com.github.mcollovati.quarkus.hilla;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CopilotDevToolsMessageHandlerTest {

    private static final String COPILOT_COORDINATES = "com.vaadin:copilot";
    private static final String DEV_TOOLS_MESSAGE_HANDLER_SERVICE =
            "META-INF/services/com.vaadin.base.devserver.DevToolsMessageHandler";

    @Test
    void runtimeExtensionsDoNotRemoveCopilotDevToolsMessageHandler() throws Exception {
        Path root = projectRoot();

        assertNoCopilotDevToolsMessageHandlerRemoval(root.resolve("lit/runtime/pom.xml"));
        assertNoCopilotDevToolsMessageHandlerRemoval(root.resolve("react/runtime/pom.xml"));
    }

    private static void assertNoCopilotDevToolsMessageHandlerRemoval(Path pom) throws Exception {
        List<String> removedResources = copilotRemovedResources(pom);

        assertFalse(
                removedResources.contains(DEV_TOOLS_MESSAGE_HANDLER_SERVICE),
                () -> String.format("Copilot DevToolsMessageHandler must stay registered in %s", pom));
    }

    private static List<String> copilotRemovedResources(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        NodeList artifacts = document.getElementsByTagName("artifact");
        List<String> removedResources = new ArrayList<>();

        for (int i = 0; i < artifacts.getLength(); i++) {
            Node node = artifacts.item(i);
            if (node instanceof Element artifact && COPILOT_COORDINATES.equals(childText(artifact, "key"))) {
                Arrays.stream(childText(artifact, "resources").split(","))
                        .map(String::trim)
                        .filter(resource -> !resource.isEmpty())
                        .forEach(removedResources::add);
            }
        }
        return removedResources;
    }

    private static String childText(Element element, String name) {
        Node child = element.getElementsByTagName(name).item(0);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static Path projectRoot() {
        String multiModuleProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (multiModuleProjectDirectory != null) {
            Path root = Path.of(multiModuleProjectDirectory);
            if (Files.exists(root.resolve("react/runtime/pom.xml"))) {
                return root;
            }
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("react/runtime/pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate quarkus-hilla project root");
    }
}
