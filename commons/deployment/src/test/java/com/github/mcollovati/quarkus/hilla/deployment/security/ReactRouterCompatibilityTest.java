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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactRouterCompatibilityTest {

    @Test
    void reactRouterVersionChangeRequiresRouteMatcherReview() throws IOException {
        try (var packageLock = getClass().getClassLoader().getResourceAsStream("vaadin-dev-bundle/package-lock.json")) {
            assertNotNull(packageLock, "Vaadin frontend dependency lock is missing");
            String content = new String(packageLock.readAllBytes(), StandardCharsets.UTF_8);
            var matcher = Pattern.compile(
                            "\\\"node_modules/react-router\\\"\\s*:\\s*\\{\\s*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                    .matcher(content);

            assertTrue(matcher.find(), "React Router version is missing from Vaadin frontend dependency lock");
            assertEquals("8.3.0", matcher.group(1), "Revalidate RoutePatternMatcher against new React Router source");
        }
    }
}
