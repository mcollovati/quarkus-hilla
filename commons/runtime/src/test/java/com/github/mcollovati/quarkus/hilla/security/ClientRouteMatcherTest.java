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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.github.mcollovati.quarkus.hilla.security.ClientRouteMatcher.Kind;
import com.github.mcollovati.quarkus.hilla.security.ClientRouteMatcher.Route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ClientRouteMatcher}.
 *
 * <p>The expected routes of the plain route trees are the results of {@code matchRoutes} of react-router 7.18.4
 * (Vaadin 25.1 and 25.2) and 8.4.0 (Vaadin 25.3 and 25.4) for the same trees. Most trees are those of React Router's
 * own tests in {@code packages/react-router/__tests__/path-matching-test.tsx}. The expectations of the Hilla route
 * trees were checked against the client route tree that {@code RouterConfigurationBuilder} of
 * {@code @vaadin/hilla-file-router} builds for the generated {@code routes.tsx}.
 */
class ClientRouteMatcherTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    // path-matching-test.tsx: "precedence of a bunch of routes in a flat route config"
    @Test
    void requirements_flatRoutePrecedence() {
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

    // path-matching-test.tsx: "precedence of a bunch of routes in a nested route config"
    @Test
    void requirements_nestedRoutesIncludeAncestors() {
        ClientRouteMatcher matcher = matcher(
                route(
                        "courses",
                        "courses",
                        index("coursesIndex"),
                        route(":id", "course", route("subjects", "subjects")),
                        route("new", "new"),
                        route("*", "coursesSplat")),
                route("/", "root"),
                route("*", "notFound"));

        assertMatch(matcher, "/courses", "courses", "coursesIndex");
        assertMatch(matcher, "/courses/routing", "courses", "course");
        assertMatch(matcher, "/courses/routing/subjects", "courses", "course", "subjects");
        assertMatch(matcher, "/courses/new", "courses", "new");
        assertMatch(matcher, "/courses/whatever/path", "courses", "coursesSplat");
        assertMatch(matcher, "/whatever", "notFound");
        assertMatch(matcher, "/", "root");
    }

    // path-matching-test.tsx: "nested index route vs sibling static route"
    @Test
    void requirements_staticRouteOutranksNestedIndexOfParameter() {
        ClientRouteMatcher matcher = matcher(route(":page", "page", index("pageIndex")), route("page", "static"));

        assertMatch(matcher, "/page", "static");
        assertMatch(matcher, "/other", "page", "pageIndex");
    }

    // path-matching-test.tsx: "dynamic segments can contain dashes"
    @Test
    void requirements_parameterNamesContainDashes() {
        ClientRouteMatcher matcher = matcher(route(":foo-bar", "dynamic"), route("foo-bar", "static"));

        assertMatch(matcher, "/foo-bar", "static");
        assertMatch(matcher, "/whatever", "dynamic");
    }

    // path-matching-test.tsx: "ranks a static routes above dynamic params with a static suffix", which holds for
    // React Router 8 only: version 7 scores ":lang.xml" like a static segment and takes the route listed first.
    @Test
    void requirements_suffixedParameterVersusStaticCombinesBothVersions() {
        ClientRouteMatcher langFirst = matcher(route("/:lang.xml", "lang"), route("/sitemap.xml", "sitemap"));
        ClientRouteMatcher sitemapFirst = matcher(route("/sitemap.xml", "sitemap"), route("/:lang.xml", "lang"));

        // React Router 7 renders "lang", 8 renders "sitemap".
        assertMatch(langFirst, "/sitemap.xml", "lang", "sitemap");
        // Both render "sitemap"; the tie of version 7 is decided by the order, which is not known for Hilla routes.
        assertMatch(sitemapFirst, "/sitemap.xml", "lang", "sitemap");
        assertMatch(langFirst, "/fr.xml", "lang");
        assertMatch(sitemapFirst, "/fr.xml", "lang");
    }

    // path-matching-test.tsx: "ranks dynamic params with a suffix above dynamic params"
    @Test
    void requirements_suffixedParameterOutranksParameter() {
        ClientRouteMatcher matcher = matcher(route("/:b", "b"), route("/:a.json", "a"));

        assertMatch(matcher, "/thing.json", "a");
        assertMatch(matcher, "/thing", "b");
    }

    // path-matching-test.tsx: "does not support partial path matching with named parameters"; Hilla generates
    // "item-:id" from a file named "item-{id}.tsx".
    @Test
    void requirements_parameterWithinSegmentIsLiteral() {
        ClientRouteMatcher matcher = matcher(route("/prefix:id", "prefix"), route("item-:id", "item"));

        assertMatch(matcher, "/prefix:id", "prefix");
        assertNoMatch(matcher, "/prefixabc");
        assertNoMatch(matcher, "/prefix/abc");
        assertMatch(matcher, "/item-:id", "item");
        assertNoMatch(matcher, "/item-42");
    }

    // path-matching-test.tsx: "path matching with splats"
    @Test
    void requirements_splat() {
        ClientRouteMatcher matcher = matcher(route("users/:id/files/*", "files"));

        assertMatch(matcher, "/users/mj/files/secrets.txt", "files");
        assertMatch(matcher, "/users/mj/files", "files");
        assertNoMatch(matcher, "/users/mj/filesssss/secrets.txt");
    }

    // path-matching-test.tsx: "path matching with optional segments" and "with optional dynamic segments"
    @Test
    void requirements_optionalSegments() {
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

    // path-matching-test.tsx: "prefers optional static over optional dynamic segments"
    @Test
    void requirements_optionalSegmentTieCombinesCandidates() {
        ClientRouteMatcher matcher = matcher(route(
                "/one",
                "one",
                route(":param?", "param", route("three", "paramThree")),
                route("two?", "two", route("three", "twoThree"))));

        assertMatch(matcher, "/one/two/three", "one", "two", "twoThree");
        assertMatch(matcher, "/one/not-two/three", "one", "param", "paramThree");
        // Both branches score the same; React Router renders the first one, "param > paramThree".
        assertMatch(matcher, "/one/three", "one", "param", "paramThree", "two", "twoThree");
    }

    // path-matching-test.tsx: "prefers index routes over optional static segments"
    @Test
    void requirements_indexOutranksOptionalSegment() {
        ClientRouteMatcher matcher =
                matcher(route("/one", "one", route(":param?", "param", route("three?", "three"), index("idx"))));

        assertMatch(matcher, "/one/two", "one", "param", "idx");
        assertMatch(matcher, "/one", "one", "param", "idx");
        assertMatch(matcher, "/one/three", "one", "param", "three");
    }

    @Test
    void requirements_staticSiblingOutranksParameterSibling() {
        ClientRouteMatcher matcher = matcher(route("users", "users", route("me", "me"), route(":id", "user")));

        assertMatch(matcher, "/users/me", "users", "me");
        assertMatch(matcher, "/users/42", "users", "user");
        assertMatch(matcher, "/USERS/ME", "users", "me");
        assertMatch(matcher, "/Users/Me/", "users", "me");
    }

    // React Router matches with a JavaScript regular expression with the "i" flag and without the "u" flag.
    @Test
    void requirements_caseInsensitiveLikeJavaScript() {
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
    void requirements_decodesPathSegments() {
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
    void decodePath_keepsWholePathIfASegmentIsMalformed() {
        assertEquals("/caf%C3%A9/%ZZ", ClientRouteMatcher.decodePath("/caf%C3%A9/%ZZ"));
        assertEquals("/café/a%2Fb", ClientRouteMatcher.decodePath("/caf%C3%A9/a%2fb"));
        assertEquals("/a+b", ClientRouteMatcher.decodePath("/a+b"));
    }

    @Test
    void requirements_trailingAndRepeatedSlashes() {
        ClientRouteMatcher matcher = matcher(route("orders", "orders"));

        assertMatch(matcher, "/orders/", "orders");
        assertMatch(matcher, "/orders//", "orders");
        assertNoMatch(matcher, "//orders");
        assertNoMatch(matcher, "/orders/x");
    }

    @Test
    void requirements_absoluteChildPathIsNotPrefixedTwice() {
        ClientRouteMatcher matcher =
                matcher(route("orders", "orders", route("/orders/:id", "detail"), route("/orders/:id/edit", "edit")));

        assertMatch(matcher, "/orders/42", "orders", "detail");
        assertMatch(matcher, "/orders/42/edit", "orders", "edit");
        assertMatch(matcher, "/orders", "orders");
        assertNoMatch(matcher, "/orders/orders/42");
    }

    @Test
    void requirements_invalidAbsoluteChildPathIsIgnored() {
        ClientRouteMatcher matcher =
                matcher(route("orders", "orders", route("/invoices/:id", "invoice"), route(":id", "order")));

        assertMatch(matcher, "/orders/42", "orders", "order");
        assertNoMatch(matcher, "/invoices/42");
    }

    @Test
    void requirements_stripsBasenameCaseInsensitively() {
        ClientRouteMatcher matcher =
                new ClientRouteMatcher(List.of(route("orders", "orders", route(":id", "order"))), "/app/");

        assertMatch(matcher, "/app/orders/1", "orders", "order");
        assertMatch(matcher, "/APP/Orders/1", "orders", "order");
        assertNoMatch(matcher, "/application/orders/1");
        assertNoMatch(matcher, "/orders/1");
        assertNoMatch(matcher, "/app");
    }

    @Test
    void computeScore_versionsDifferOnlyForSuffixedParameters() {
        int staticSegment = ClientRouteMatcher.computeScore("/sitemap.xml", false, false);
        int parameter = ClientRouteMatcher.computeScore("/:lang", false, false);

        assertEquals(staticSegment, ClientRouteMatcher.computeScore("/:lang.xml", false, false));
        int version8 = ClientRouteMatcher.computeScore("/:lang.xml", false, true);
        assertTrue(parameter < version8 && version8 < staticSegment);
        for (String path : List.of("/", "/a/:b/*", "/a/:b/c", "/:a-b/c", "/a/b.c")) {
            assertEquals(
                    ClientRouteMatcher.computeScore(path, true, false),
                    ClientRouteMatcher.computeScore(path, true, true),
                    path);
        }
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
    void fileRoutes_rootLayout() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"root","route":"","children":[
                  {"title":"index","route":""},
                  {"title":"login","route":"login"},
                  {"title":"about","route":"about"},
                  {"title":"orders","route":"orders","loginRequired":true,"children":[
                    {"title":"ordersIndex","route":""},{"title":"order","route":":id"},{"title":"ordersNew","route":"new"}]},
                  {"title":"users","route":"users","children":[
                    {"title":"me","route":"me","loginRequired":true},
                    {"title":"user","route":":id","rolesAllowed":["ADMIN"]}]},
                  {"title":"docs","route":"docs","children":[{"title":"docsIndex","route":""}]},
                  {"title":"wildcard","route":"*"}
                ]}]
                """);

        assertMatch(matcher, "/", "root", "index");
        assertMatch(matcher, "/login", "root", "login");
        assertMatch(matcher, "/orders", "root", "orders", "ordersIndex");
        assertMatch(matcher, "/orders/42", "root", "orders", "order");
        assertMatch(matcher, "/Orders/42/", "root", "orders", "order");
        assertMatch(matcher, "/orders/%34%32", "root", "orders", "order");
        assertMatch(matcher, "/orders/new", "root", "orders", "ordersNew");
        assertMatch(matcher, "/users/me", "root", "users", "me");
        assertMatch(matcher, "/users/42", "root", "users", "user");
        assertMatch(matcher, "/nowhere", "root", "wildcard");
        // The client renders "root > wildcard": Hilla duplicates the root layout for its eagerly loaded views and
        // adds no Flow fallback to the copy that holds the other views. With withDeepWildcard(), it renders the
        // wildcard view within the layout, whose requirements are kept therefore.
        assertMatch(matcher, "/docs/missing", "root", "docs", "wildcard");
        assertMatch(matcher, "/orders/42/edit", "root", "orders", "wildcard");
    }

    @Test
    void fileRoutes_withoutRootLayout() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"login","route":"login"},
                 {"title":"orders","route":"orders","loginRequired":true,"children":[
                   {"title":"ordersIndex","route":""},{"title":"ordersNew","route":"new"},
                   {"title":"ordersEdit","route":"/orders/:id/edit"}]},
                 {"title":"users","route":"users","children":[
                   {"title":"me","route":"me","loginRequired":true},
                   {"title":"user","route":":id","rolesAllowed":["ADMIN"]}]},
                 {"title":"wildcard","route":"*","loginRequired":true}]
                """);

        assertMatch(matcher, "/login", "login");
        assertMatch(matcher, "/orders/42/edit", "orders", "ordersEdit");
        assertMatch(matcher, "/ORDERS/new/", "orders", "ordersNew");
        assertMatch(matcher, "/users/me", "users", "me");
        assertMatch(matcher, "/users/7", "users", "user");
        assertMatch(matcher, "/x", "wildcard");
        // The client renders the Flow fallback of "orders" ("users"); with withDeepWildcard(), the wildcard view.
        assertMatch(matcher, "/orders/42", "orders", "wildcard");
        assertMatch(matcher, "/users/x/y", "users", "wildcard");
        // The client renders the Flow index fallback; without fallbacks, the wildcard view.
        assertMatch(matcher, "/", "wildcard");
    }

    @Test
    void fileRoutes_suffixedParameters() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"root","route":"","loginRequired":true,"children":[
                  {"title":"index","route":""},
                  {"title":"report","route":":id.json","rolesAllowed":["ADMIN"]},
                  {"title":"sitemap","route":"sitemap.json"},
                  {"title":"item","route":"item-:id"},
                  {"title":"flowLayout","route":"flow-layout","flowLayout":true}
                ]}]
                """);

        assertMatch(matcher, "/", "root", "index");
        // React Router 7 renders "report", 8 renders "sitemap".
        assertMatch(matcher, "/sitemap.json", "root", "report", "sitemap");
        assertMatch(matcher, "/x.json", "root", "report");
        assertMatch(matcher, "/item-:id", "root", "item");
        assertNoMatch(matcher, "/item-42");
        assertMatch(matcher, "/flow-layout", "root", "flowLayout");
    }

    @Test
    void fileRoutes_optionalSegments() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"root","route":"","children":[
                  {"title":"index","route":""},
                  {"title":"about","route":"about"},
                  {"title":"lang","route":":lang?","children":[{"title":"langAbout","route":"about","loginRequired":true}]},
                  {"title":"report","route":":id.json","rolesAllowed":["ADMIN"]}
                ]}]
                """);

        // Both score the same; the client renders the route listed first.
        assertMatch(matcher, "/about", "root", "about", "lang", "langAbout");
        assertMatch(matcher, "/en/about", "root", "lang", "langAbout");
        assertMatch(matcher, "/de", "root", "lang");
        // React Router 8 ranks the Flow index fallback of ":lang?" first, which Hilla does not add here: the root
        // layout is split for its eagerly loaded index view. The best Hilla view is "report" for both versions.
        assertMatch(matcher, "/x.json", "root", "report");
    }

    @Test
    void fileRoutes_flowFallbackIsLeftToFlow() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"root","route":"","loginRequired":true,"children":[
                  {"title":"index","route":""},
                  {"title":"orders","route":"orders","children":[{"title":"order","route":":id"}]}
                ]}]
                """);

        // The client renders Flow for these paths, which may also belong to REST resources or static files.
        assertNoMatch(matcher, "/api/orders");
        assertNoMatch(matcher, "/orders/42/edit");
        assertMatch(matcher, "/orders/42", "root", "orders", "order");
        // The client renders the Flow index fallback within the layout, or the layout itself without fallbacks.
        assertMatch(matcher, "/orders", "root", "orders");
    }

    @Test
    void fileRoutes_layoutKeepsRequirementsOfParameterizedChildNextToPublicWildcard() {
        ClientRouteMatcher withChild = fileRoutes("""
                [{"title":"orders","route":"orders","loginRequired":true,"children":[{"title":"order","route":":id"}]},
                 {"title":"wildcard","route":"*"}]
                """);
        ClientRouteMatcher withoutChild = fileRoutes("""
                [{"title":"orders","route":"orders","loginRequired":true,"children":[{"title":"ordersIndex","route":""}]},
                 {"title":"wildcard","route":"*"}]
                """);

        assertMatch(withChild, "/orders/42", "orders", "order");
        assertMatch(withChild, "/somewhere", "wildcard");
        assertMatch(withoutChild, "/orders/42", "orders", "wildcard");
    }

    @Test
    void fileRoutes_nestedLayoutsInheritClosestWildcard() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"admin","route":"admin","loginRequired":true,"children":[
                   {"title":"adminWildcard","route":"*","rolesAllowed":["ADMIN"]},
                   {"title":"reports","route":"reports","children":[{"title":"reportsIndex","route":""}]}]},
                 {"title":"wildcard","route":"*"}]
                """);

        assertMatch(matcher, "/admin/missing", "admin", "adminWildcard");
        assertMatch(matcher, "/admin/reports/missing", "admin", "reports", "adminWildcard");
        assertMatch(matcher, "/missing", "wildcard");
    }

    @Test
    void fileRoutes_bestViewOfEachVersionWhereFallbackIsMissing() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"root","route":"","children":[
                  {"title":"index","route":""},
                  {"title":"lang","route":":lang?","children":[
                    {"title":"report","route":":id.json","loginRequired":true},
                    {"title":"slug","route":":slug","children":[{"title":"opt","route":"opt?","rolesAllowed":["ADMIN"]}]}
                  ]}
                ]}]
                """);

        // React Router 7 renders "report". Version 8 ranks the Flow index fallback of ":slug" first, which Hilla does
        // not add below the root layout it splits for the index view, and renders "slug > opt".
        assertMatch(matcher, "/me.json", "root", "lang", "report", "slug", "opt");
    }

    @Test
    void fileRoutes_flowLayoutsMayReceiveWildcardOfAnyEnclosingLevel() {
        ClientRouteMatcher matcher = fileRoutes("""
                [{"title":"slug","route":":slug","flowLayout":true},
                 {"title":"orders","route":"Orders","flowLayout":true,"children":[
                   {"title":"ordersWildcard","route":"*","loginRequired":true}]},
                 {"title":"wildcard","route":"*","flowLayout":true,"rolesAllowed":["ADMIN"]}]
                """);

        // The client renders "orders > ordersWildcard". With withDeepWildcard() after withFallback(Flow), the copy of
        // "orders" within the Flow layout lacks "ordersWildcard" and receives "wildcard".
        assertMatch(matcher, "/Orders/a", "orders", "ordersWildcard", "wildcard");
    }

    @Test
    void fileRoutes_withoutRoutes() {
        ClientRouteMatcher matcher = ClientRouteMatcher.forFileRoutes(List.of(), "/");

        assertNoMatch(matcher, "/");
        assertNoMatch(matcher, "/anything");
    }

    private static ClientRouteMatcher matcher(Route... routes) {
        return new ClientRouteMatcher(List.of(routes), "/");
    }

    private static ClientRouteMatcher fileRoutes(String json) {
        return ClientRouteMatcher.forFileRoutes(
                MAPPER.readValue(json, new TypeReference<List<AvailableViewInfo>>() {}), "/");
    }

    private static Route route(String path, String id, Route... children) {
        return new Route(path, false, view(id, path), Kind.VIEW, children.length == 0 ? null : List.of(children));
    }

    private static Route index(String id) {
        return new Route(null, true, view(id, ""), Kind.VIEW, null);
    }

    private static AvailableViewInfo view(String id, String path) {
        return new AvailableViewInfo(id, null, false, path, false, false, null, null, null, false, null);
    }

    private static void assertMatch(ClientRouteMatcher matcher, String path, String... expected) {
        Optional<List<AvailableViewInfo>> requirements = matcher.requirements(path);
        assertTrue(requirements.isPresent(), () -> "No route for " + path);
        Set<String> titles =
                requirements.get().stream().map(AvailableViewInfo::title).collect(Collectors.toSet());
        assertEquals(Set.copyOf(Arrays.asList(expected)), titles, path);
    }

    private static void assertNoMatch(ClientRouteMatcher matcher, String path) {
        Optional<List<AvailableViewInfo>> requirements = matcher.requirements(path);
        assertTrue(requirements.isEmpty(), () -> path + " matched " + requirements.get());
    }
}
