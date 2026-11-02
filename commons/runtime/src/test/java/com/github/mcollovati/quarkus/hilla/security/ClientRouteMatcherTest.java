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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the flattened route matching ported from React Router's path-matching tests.
 */
class ClientRouteMatcherTest {

    @Test
    void match_flatRoutePrecedence() {
        ClientRouteMatcher matcher = matcher(
                route("/groups/main/users/me", "g-main-users-me"),
                route("/groups/:groupId/users/me", "g-id-users-me"),
                route("/groups/:groupId/users/:userId", "g-id-users-uid"),
                route("/groups/:groupId/users/*", "g-id-users-splat"),
                route("/groups/main/users", "g-main-users"),
                route("/groups/:groupId/users", "g-id-users"),
                route("/groups/main", "g-main"),
                route("/groups/:groupId", "g-id"),
                route("/groups", "g"),
                route("/files/*", "files-splat"),
                route("/files", "files"),
                route("/:one/:two/:three/:four/:five", "five"),
                route("/", "root"),
                route("*", "splat"));

        assertMatch(matcher, "/groups/main/users/me", "g-main-users-me");
        assertMatch(matcher, "/groups/other/users/me", "g-id-users-me");
        assertMatch(matcher, "/groups/123/users/456", "g-id-users-uid");
        assertMatch(matcher, "/groups/main/users/a/b", "g-id-users-splat");
        assertMatch(matcher, "/groups/main/users", "g-main-users");
        assertMatch(matcher, "/groups/123/users", "g-id-users");
        assertMatch(matcher, "/groups/main", "g-main");
        assertMatch(matcher, "/groups/123", "g-id");
        assertMatch(matcher, "/groups", "g");
        assertMatch(matcher, "/files/some/long/path", "files-splat");
        assertMatch(matcher, "/files", "files");
        assertMatch(matcher, "/one/two/three/four/five", "five");
        assertMatch(matcher, "/", "root");
        assertMatch(matcher, "/no/where", "splat");
    }

    @Test
    void match_parameterNamesContainDashes() {
        ClientRouteMatcher matcher = matcher(route(":foo-bar", "dynamic"), route("foo-bar", "static"));

        assertMatch(matcher, "/foo-bar", "static");
        assertMatch(matcher, "/whatever", "dynamic");
    }

    @Test
    void match_suffixedParameterOutranksParameter() {
        ClientRouteMatcher matcher = matcher(route("/:b", "b"), route("/:a.json", "a"));

        assertMatch(matcher, "/thing.json", "a");
        assertMatch(matcher, "/thing", "b");
    }

    @Test
    void match_parameterWithinSegmentIsLiteral() {
        ClientRouteMatcher matcher = matcher(route("/prefix:id", "prefix"), route("item-:id", "item"));

        assertMatch(matcher, "/prefix:id", "prefix");
        assertNoMatch(matcher, "/prefixabc");
        assertNoMatch(matcher, "/prefix/abc");
        assertMatch(matcher, "/item-:id", "item");
        assertNoMatch(matcher, "/item-42");
    }

    @Test
    void match_splat() {
        ClientRouteMatcher matcher = matcher(route("users/:id/files/*", "files"));

        assertMatch(matcher, "/users/mj/files/secrets.txt", "files");
        assertMatch(matcher, "/users/mj/files", "files");
        assertNoMatch(matcher, "/users/mj/filesssss/secrets.txt");
    }

    @Test
    void match_optionalSegments() {
        ClientRouteMatcher optionalStart = matcher(route("/en?/abc", "en"));
        ClientRouteMatcher optionalEnd = matcher(route("/nested/one?/two?", "nested"));
        ClientRouteMatcher optionalParameter = matcher(route("/:lang?/abc", "lang"));
        ClientRouteMatcher optionalSuffixedParameter = matcher(route("/sitemap/:lang?.xml", "sitemap"));

        assertMatch(optionalStart, "/abc", "en");
        assertMatch(optionalStart, "/en/abc", "en");
        assertNoMatch(optionalStart, "/");
        assertNoMatch(optionalStart, "/en/abc/bar");
        assertMatch(optionalEnd, "/nested", "nested");
        assertMatch(optionalEnd, "/nested/one", "nested");
        assertMatch(optionalEnd, "/nested/one/two", "nested");
        assertNoMatch(optionalEnd, "/nested/one/two/baz");
        assertMatch(optionalParameter, "/abc", "lang");
        assertMatch(optionalParameter, "/en/abc", "lang");
        assertNoMatch(optionalParameter, "/");
        assertNoMatch(optionalParameter, "/en/abc/bar");
        assertMatch(optionalSuffixedParameter, "/sitemap/blah.xml", "sitemap");
        assertMatch(optionalSuffixedParameter, "/sitemap/.xml", "sitemap");
        assertNoMatch(optionalSuffixedParameter, "/sitemap.xml");
        assertNoMatch(optionalSuffixedParameter, "/sitemap/blah");
    }

    @Test
    void match_caseInsensitiveLikeJavaScript() {
        ClientRouteMatcher matcher = matcher(
                route("über", "ueber"),
                route("straße", "strasse"),
                route("k", "k"),
                route("µ", "micro"),
                route("s", "s"));

        assertMatch(matcher, "/%C3%9CBER", "ueber");
        assertMatch(matcher, "/%C3%BCber", "ueber");
        assertMatch(matcher, "/STRA%C3%9FE", "strasse");
        assertMatch(matcher, "/K", "k");
        assertMatch(matcher, "/S", "s");
        // MICRO SIGN, GREEK SMALL LETTER MU and GREEK CAPITAL LETTER MU share their upper case form.
        assertMatch(matcher, "/%C2%B5", "micro");
        assertMatch(matcher, "/%CE%BC", "micro");
        assertMatch(matcher, "/%CE%9C", "micro");
        // An upper case form of more than one character, or an ASCII one of a non-ASCII character, does not match.
        assertNoMatch(matcher, "/STRASSE");
        assertNoMatch(matcher, "/%E2%84%AA");
        assertNoMatch(matcher, "/%C5%BF");
    }

    @Test
    void match_decodesPathSegments() {
        ClientRouteMatcher matcher = matcher(route("café", "cafe"), route("items/:id", "item"), route("a b", "space"));

        assertMatch(matcher, "/caf%C3%A9", "cafe");
        assertMatch(matcher, "/caf%c3%a9/", "cafe");
        assertMatch(matcher, "/CAF%C3%89", "cafe");
        assertMatch(matcher, "/a%20b", "space");
        assertMatch(matcher, "/items/a%20b", "item");
        // An encoded slash stays within its segment.
        assertMatch(matcher, "/items/a%2Fb", "item");
        assertNoMatch(matcher, "/items/a/b");
        // A path that cannot be decoded is matched as it is.
        assertMatch(matcher, "/items/%E0%A4%A", "item");
        assertMatch(matcher, "/items/%ZZ", "item");
        assertMatch(matcher, "/items/%C0%AF", "item");
    }

    @Test
    void match_trailingAndRepeatedSlashes() {
        ClientRouteMatcher matcher = matcher(route("orders", "orders"));

        assertMatch(matcher, "/orders/", "orders");
        assertMatch(matcher, "/orders//", "orders");
        assertNoMatch(matcher, "//orders");
        assertNoMatch(matcher, "/orders/x");
    }

    @Test
    void decodePath_keepsWholePathIfASegmentIsMalformed() {
        assertEquals("/caf%C3%A9/%ZZ", ClientRouteMatcher.decodePath("/caf%C3%A9/%ZZ"));
        assertEquals("/café/a%2Fb", ClientRouteMatcher.decodePath("/caf%C3%A9/a%2fb"));
        assertEquals("/a+b", ClientRouteMatcher.decodePath("/a+b"));
    }

    @Test
    void explodeOptionalSegments_likeReactRouter() {
        assertEquals(
                List.of("/nested/one/two", "/nested/one", "/nested/two", "/nested"),
                ClientRouteMatcher.explodeOptionalSegments("/nested/one?/two?"));
        assertEquals(List.of(":lang/abc", "abc"), ClientRouteMatcher.explodeOptionalSegments(":lang?/abc"));
        assertEquals(List.of(":lang", ""), ClientRouteMatcher.explodeOptionalSegments(":lang?"));
    }

    @Test
    void match_staticRouteOutranksParameterAndWildcard() {
        ClientRouteMatcher matcher =
                matcher(route("*", "fallback"), route("users/:id", "user"), route("users/me", "me"));

        assertMatch(matcher, "/users/me", "me");
        assertMatch(matcher, "/users/42", "user");
        assertMatch(matcher, "/elsewhere", "fallback");
    }

    @Test
    void match_staticRouteOutranksSuffixedParameter() {
        ClientRouteMatcher matcher = matcher(route(":lang.xml", "lang"), route("sitemap.xml", "sitemap"));

        assertMatch(matcher, "/sitemap.xml", "sitemap");
        assertMatch(matcher, "/fr.xml", "lang");
    }

    @Test
    void match_equalScoresReturnAllPublicCandidates() {
        AvailableViewInfo first = route("users/:id", "first");
        AvailableViewInfo second = route("users/:name", "second");
        ClientRouteMatcher matcher = matcher(first, second, route("users/*", "fallback"));

        assertEquals(
                Set.of(first, second), Set.copyOf(matcher.match("/users/42").orElseThrow()));
    }

    @Test
    void match_optionalParentExpansionsReturnAllBestCandidates() {
        AvailableViewInfo secured = route(":id/foo", "secured", true);
        AvailableViewInfo publicView = route("foo", "public");
        ClientRouteMatcher matcher = matcher(route(":lang?", "layout", secured, publicView));

        var candidates = matcher.match("/en/foo").orElseThrow();
        assertEquals(Set.of(secured, publicView), Set.copyOf(candidates));
        assertTrue(candidates.stream().anyMatch(AvailableViewInfo::loginRequired));
    }

    @Test
    void match_skipLayoutsMetadataReturnsAllBestCandidates() {
        var mapper = JsonMapper.builder()
                .disable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        List<AvailableViewInfo> routes = mapper.readValue("""
                [{"route":":id"}, {"route":":name", "loginRequired":true, "skipLayouts":true}]
                """, new TypeReference<List<AvailableViewInfo>>() {});
        ClientRouteMatcher matcher = ClientRouteMatcher.forFileRoutes(routes, "/");

        var candidates = matcher.match("/42").orElseThrow();
        assertEquals(Set.copyOf(routes), Set.copyOf(candidates));
        assertTrue(candidates.stream().anyMatch(AvailableViewInfo::loginRequired));
    }

    @Test
    void match_rootAndOptionalParameter() {
        ClientRouteMatcher matcher = matcher(route("", "root"), route("optional/:id?", "optional"));

        assertMatch(matcher, "/", "root");
        assertMatch(matcher, "", "root");
        assertMatch(matcher, null, "root");
        assertMatch(matcher, "/optional", "optional");
        assertMatch(matcher, "/optional/42/", "optional");
        assertNoMatch(matcher, "/optional/42/edit");
    }

    @Test
    void match_noRoutes() {
        ClientRouteMatcher matcher = new ClientRouteMatcher(Map.of(), "/");

        assertNoMatch(matcher, "/");
        assertNoMatch(matcher, "/anything");
    }

    @Test
    void match_nestedRoutesUseOnlyWinningView() {
        ClientRouteMatcher matcher = matcher(route(
                "courses",
                "layout",
                route("", "index"),
                route(":id", "course", route("subjects", "subjects")),
                route("new", "new"),
                route("*", "coursesSplat")));

        assertMatch(matcher, "/courses", "index");
        assertMatch(matcher, "/courses/routing", "course");
        assertMatch(matcher, "/courses/routing/subjects", "subjects");
        assertMatch(matcher, "/courses/new", "new");
        assertMatch(matcher, "/courses/whatever/path", "coursesSplat");
    }

    @Test
    void match_absoluteAndOptionalChildPaths() {
        ClientRouteMatcher matcher =
                matcher(route("orders", "orders", route("/orders/:id/edit", "edit"), route(":id?/details", "details")));

        assertMatch(matcher, "/orders/42/edit", "edit");
        assertMatch(matcher, "/orders/details", "details");
        assertMatch(matcher, "/orders/42/details", "details");
        assertNoMatch(matcher, "/orders/orders/42/edit");
    }

    @Test
    void match_stripsBasenameCaseInsensitively() {
        ClientRouteMatcher matcher = ClientRouteMatcher.forFileRoutes(List.of(route("orders/:id", "order")), "/app/");

        assertMatch(matcher, "/app/orders/1", "order");
        assertMatch(matcher, "/APP/Orders/1", "order");
        assertNoMatch(matcher, "/application/orders/1");
        assertNoMatch(matcher, "/orders/1");
    }

    @Test
    void match_unknownOrderReturnsAllBestCandidates() {
        AvailableViewInfo first = route("users/:id", "first");
        AvailableViewInfo second = route("users/:name", "second");
        for (List<AvailableViewInfo> order : List.of(List.of(first, second), List.of(second, first))) {
            Map<String, AvailableViewInfo> routes = new LinkedHashMap<>();
            order.forEach(view -> routes.put(view.route(), view));
            routes.put("users/*", route("users/*", "fallback"));
            ClientRouteMatcher matcher = new ClientRouteMatcher(routes, "/");

            assertEquals(2, matcher.match("/users/42").orElseThrow().size());
            assertTrue(matcher.match("/users/42").orElseThrow().containsAll(List.of(first, second)));
        }
    }

    private static ClientRouteMatcher matcher(AvailableViewInfo... views) {
        return ClientRouteMatcher.forFileRoutes(List.of(views), "/");
    }

    private static AvailableViewInfo route(String path, String id, AvailableViewInfo... children) {
        return route(path, id, false, children);
    }

    private static AvailableViewInfo route(
            String path, String id, boolean loginRequired, AvailableViewInfo... children) {
        return new AvailableViewInfo(
                id,
                null,
                loginRequired,
                path,
                false,
                false,
                null,
                children.length == 0 ? null : List.of(children),
                null,
                false,
                null);
    }

    private static void assertMatch(ClientRouteMatcher matcher, String path, String expected) {
        assertEquals(
                List.of(expected),
                matcher.match(path).orElseThrow(() -> new AssertionError("No route for " + path)).stream()
                        .map(AvailableViewInfo::title)
                        .toList(),
                path);
    }

    private static void assertNoMatch(ClientRouteMatcher matcher, String path) {
        assertTrue(matcher.match(path).isEmpty(), () -> "Unexpected match for " + path);
    }
}
