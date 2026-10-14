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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePatternMatcherTest {

    /*
     * Golden winners captured from the public React Router 8.3 matchRoutes API.
     * ReactRouterCompatibilityTest forces review of these cases when Vaadin's
     * managed React Router version changes.
     */

    @Test
    void reactRouter8_staticRouteOutranksPartialParameter() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("files/:id.json", "parameter"),
                RoutePatternMatcher.compile("files/public.json", "static"));

        assertEquals(List.of("static"), targets(RoutePatternMatcher.bestMatches(routes, "/files/public.json")));
    }

    @Test
    void reactRouter8_partialParameterOutranksDynamicParameter() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("files/:id", "dynamic"),
                RoutePatternMatcher.compile("files/:id.json", "partial"));

        assertEquals(List.of("partial"), targets(RoutePatternMatcher.bestMatches(routes, "/files/report.json")));
    }

    @Test
    void reactRouter8_dynamicParameterOutranksWildcard() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("files/*", "wildcard"),
                RoutePatternMatcher.compile("files/:id", "dynamic"));

        assertEquals(List.of("dynamic"), targets(RoutePatternMatcher.bestMatches(routes, "/files/report")));
    }

    @Test
    void reactRouter8_optionalStaticOutranksDynamicParameter() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("docs/:id", "dynamic"),
                RoutePatternMatcher.compile("docs/edit?", "optional-static"));

        assertEquals(List.of("optional-static"), targets(RoutePatternMatcher.bestMatches(routes, "/docs/edit")));
    }

    @Test
    void equalRankReturnsAllRoutesForConservativeAuthorization() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("projects", "short"),
                RoutePatternMatcher.compile("projects/:id?", "optional-tail"));

        assertEquals(List.of("short", "optional-tail"), targets(RoutePatternMatcher.bestMatches(routes, "/projects")));
    }

    @Test
    void bestMatches_indexRouteOutranksOmittedOptionalStaticRoute() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("", true, "index"), RoutePatternMatcher.compile("projects?", "optional"));

        assertEquals(List.of("index"), targets(RoutePatternMatcher.bestMatches(routes, "/")));
    }

    @Test
    void bestMatches_requiredParameterConsumesOneSegment() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("users/:id", "required"));

        assertEquals(List.of("required"), targets(RoutePatternMatcher.bestMatches(routes, "/users/42")));
        assertTrue(RoutePatternMatcher.bestMatches(routes, "/users").isEmpty());
    }

    @Test
    void bestMatches_optionalParameterMatchesWithAndWithoutValue() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("reports/:filter?", "optional"));

        assertEquals(List.of("optional"), targets(RoutePatternMatcher.bestMatches(routes, "/reports")));
        assertEquals(List.of("optional"), targets(RoutePatternMatcher.bestMatches(routes, "/reports/open")));
    }

    @Test
    void bestMatches_optionalStaticSegmentMatchesWithAndWithoutSegment() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("projects?", "optional-static"));

        assertEquals(List.of("optional-static"), targets(RoutePatternMatcher.bestMatches(routes, "/")));
        assertEquals(List.of("optional-static"), targets(RoutePatternMatcher.bestMatches(routes, "/projects")));
    }

    @Test
    void bestMatches_preservesEncodedSlashInsideParameter() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("items/:id", "parameter"));

        assertEquals(List.of("parameter"), targets(RoutePatternMatcher.bestMatches(routes, "/items/a%2Fb")));
        assertTrue(RoutePatternMatcher.bestMatches(routes, "/items/a/b").isEmpty());
    }

    @Test
    void bestMatches_parameterSuffixMustMatchBeforeOutrankingWildcard() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes = List.of(
                RoutePatternMatcher.compile("files/:id.json", "suffix"),
                RoutePatternMatcher.compile("files/*", "wildcard"));

        assertEquals(List.of("wildcard"), targets(RoutePatternMatcher.bestMatches(routes, "/files/foo")));
        assertEquals(List.of("suffix"), targets(RoutePatternMatcher.bestMatches(routes, "/files/foo.json")));
    }

    @Test
    void bestMatches_optionalParameterWithSuffixKeepsSegmentRequired() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/:id?.json", "suffix"));

        assertTrue(RoutePatternMatcher.bestMatches(routes, "/files").isEmpty());
        assertEquals(List.of("suffix"), targets(RoutePatternMatcher.bestMatches(routes, "/files/.json")));
        assertEquals(List.of("suffix"), targets(RoutePatternMatcher.bestMatches(routes, "/files/foo.json")));
    }

    @Test
    void bestMatches_trailingQuestionMarksAnySegmentOptional() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("orders/:id.v2?", "optional-suffix"));

        assertEquals(List.of("optional-suffix"), targets(RoutePatternMatcher.bestMatches(routes, "/orders")));
        assertEquals(List.of("optional-suffix"), targets(RoutePatternMatcher.bestMatches(routes, "/orders/x.v2")));
    }

    @Test
    void bestMatches_terminalWildcardConsumesRemainingSegments() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/*", "wildcard"));

        assertEquals(List.of("wildcard"), targets(RoutePatternMatcher.bestMatches(routes, "/files/a/b")));
    }

    @Test
    void bestMatches_wildcardBeforeOptionalParameterUsesOptionalExpansion() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/*/:id?", "wildcard-optional"));

        assertEquals(List.of("wildcard-optional"), targets(RoutePatternMatcher.bestMatches(routes, "/files/a/b")));
    }

    @Test
    void bestMatches_nonTerminalWildcardUsesLiteralBranch() {
        List<RoutePatternMatcher.CompiledRoute<String>> routes =
                List.of(RoutePatternMatcher.compile("files/*/:id", "literal-wildcard"));

        assertEquals(List.of("literal-wildcard"), targets(RoutePatternMatcher.bestMatches(routes, "/files/*/42")));
        assertTrue(RoutePatternMatcher.bestMatches(routes, "/files/a/42").isEmpty());
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
