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
package com.github.mcollovati.quarkus.hilla.security;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fails when Vaadin ships another React Router version than the one {@link ClientRouteMatcher} ports.
 */
class ReactRouterVersionTest {

    // The React Router version whose matching ClientRouteMatcher ports.
    private static final String PORTED_VERSION = "8.4.0";

    private static final String FLOW_REACT_ROUTER_PACKAGE =
            "com/vaadin/flow/server/frontend/dependencies/react-router/package.json";

    @Test
    void reactRouterVersion_matchesPortedMatching() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FLOW_REACT_ROUTER_PACKAGE)) {
            assertNotNull(input, "Flow no longer provides " + FLOW_REACT_ROUTER_PACKAGE);
            JsonNode dependencies = JsonMapper.builder().build().readTree(input).path("dependencies");
            assertEquals(
                    PORTED_VERSION,
                    dependencies.path("react-router").asString(),
                    "Vaadin ships another React Router version. Compare the matching in React Router's"
                            + " lib/router/utils.ts (flattenRoutes, rankRouteBranches, computeScore, compilePath,"
                            + " matchPath, decodePath, stripBasename, explodeOptionalSegments) with ClientRouteMatcher,"
                            + " adapt it if needed, then update PORTED_VERSION.");
        }
    }
}
