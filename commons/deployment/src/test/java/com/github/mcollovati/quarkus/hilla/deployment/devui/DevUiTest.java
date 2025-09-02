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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.config.TestConfigProviderResolver;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.mcollovati.quarkus.hilla.deployment.endpoints.TestEndpoint;

import static io.quarkus.runtime.LaunchMode.DEVELOPMENT;

public class DevUiTest {

    @RegisterExtension
    static final QuarkusDevModeTest config =
            new QuarkusDevModeTest().withApplicationRoot(jar -> jar.addClass(TestEndpoint.class));
    // .withEmptyApplication();

    private static final String CONST = "export const ";
    private static final String SPACE = " ";
    private static final String EQUALS = "=";
    private static final String NAMESPACE = "quarkus-hilla-commons";

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonFactory factory = mapper.getFactory();
    private final URI devUI;

    public DevUiTest() {
        Config config = ((TestConfigProviderResolver) ConfigProviderResolver.instance()).getConfig(DEVELOPMENT);
        String testUrl = config.getValue("test.url", String.class);
        String nonApplicationRoot = config.getOptionalValue("quarkus.http.non-application-root-path", String.class)
                .orElse("q");
        if (!nonApplicationRoot.startsWith("/")) {
            nonApplicationRoot = "/" + nonApplicationRoot;
        }
        this.devUI = URI.create(testUrl + nonApplicationRoot + "/dev-ui/");
    }

    @Test
    public void shouldGenerateHillaEndpoints() throws Exception {
        JsonNode hillaEndpoints = getBuildTimeData("hillaEndpoints");
        Assertions.assertNotNull(hillaEndpoints);
        Assertions.assertEquals(1, hillaEndpoints.size());
        JsonNode endpoint = hillaEndpoints.get(0);
        Assertions.assertEquals(
                TestEndpoint.class.getName(),
                endpoint.get("declaringClass").get("name").asText());
    }

    @Test
    public void shouldExposeSharedWebComponent() throws Exception {
        String source =
                readDataFromUrl(new URI(devUI.toString() + NAMESPACE + "/qwc-quarkus-hilla-browser-callables.js"));
        Assertions.assertNotNull(source);
        Assertions.assertTrue(
                source.contains("import {hillaEndpoints as endpoints} from './quarkus-hilla-application-data.js';"));
        Assertions.assertTrue(source.contains(
                "customElements.define('qwc-quarkus-hilla-browser-callables', QwcQuarkusHillaBrowserCallables);"));
    }

    private String readDataFromUrl(URI uri) throws IOException {
        try (Scanner scanner = new Scanner(uri.toURL().openStream(), StandardCharsets.UTF_8.toString())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : null;
        }
    }

    public JsonNode getBuildTimeData(String key) throws Exception {
        String data = readDataFromUrl(new URI(devUI.toString() + NAMESPACE + "/quarkus-hilla-application-data.js"));
        String[] kvs = data.split(CONST);

        for (String kv : kvs) {
            if (kv.startsWith(key + SPACE + EQUALS + SPACE)) {
                String json = kv.substring(kv.indexOf(EQUALS) + 1).trim();
                return toJsonNode(json);
            }
        }

        return null;
    }

    protected JsonNode toJsonNode(String json) {
        try {
            JsonParser parser = factory.createParser(json);
            return mapper.readTree(parser);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
