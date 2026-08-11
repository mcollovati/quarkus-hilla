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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutePatternMatcherTest {

    @Test
    void bestMatches_staticRouteOutranksPartialParameter() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("files/:id.json", "parameter"),
                RoutePatternMatcher.compile("files/public.json", "static"));

        assertEquals(List.of("static"), targets(RoutePatternMatcher.bestMatches(routes, "/files/public.json")));
    }

    @Test
    void bestMatches_indexRouteOutranksOmittedOptionalStaticRoute() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("", true, "index"), RoutePatternMatcher.compile("projects?", "optional"));

        assertEquals(List.of("index"), targets(RoutePatternMatcher.bestMatches(routes, "/")));
    }

    @Test
    void bestMatches_trailingSplatWithoutSlashUsesReactRouterNormalization() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/:id*", "splat"));

        assertEquals(List.of("splat"), targets(RoutePatternMatcher.bestMatches(routes, "/files/42/details")));
    }

    @Test
    void bestMatches_decodesUtf8ClientRouteSegment() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("café", "unicode"));

        assertEquals(List.of("unicode"), targets(RoutePatternMatcher.bestMatches(routes, "/caf%C3%A9")));
    }

    @Test
    void bestMatches_decodesBackslashClientRouteSegment() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/a\\b", "backslash"));

        assertEquals(List.of("backslash"), targets(RoutePatternMatcher.bestMatches(routes, "/files/a%5Cb")));
    }

    @Test
    void bestMatches_deduplicatesRouteMatchingEncodedAndDecodedCandidates() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("items/:id", "parameter"));

        assertEquals(List.of("parameter"), targets(RoutePatternMatcher.bestMatches(routes, "/items/caf%C3%A9")));
    }

    private static List<String> targets(List<RoutePatternMatcher.CompiledRoute<String>> routes) {
        return routes.stream().map(RoutePatternMatcher.CompiledRoute::target).toList();
    }
}
