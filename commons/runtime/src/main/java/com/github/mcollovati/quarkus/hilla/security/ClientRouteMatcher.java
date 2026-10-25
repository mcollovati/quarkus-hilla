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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the client route that React Router renders for a request path, so that the HTTP layer authorizes the same
 * route, with the requirements of that route and of all its ancestors.
 *
 * <p>The route tree is the one the generated {@code routes.tsx} builds with {@code @vaadin/hilla-file-router}:
 * {@code withFileRoutes(fileRoutes).withFallback(Flow).protect()}. Compared with {@code file-routes.json}, it turns
 * a view with an empty path into an index route and adds the Flow fallback routes, {@code *} and an index route, to
 * the top level and to every route with children. Matching and ranking are ported from React Router
 * ({@code flattenRoutes}, {@code explodeOptionalSegments}, {@code computeScore}, {@code matchRouteBranch},
 * {@code compilePath}, {@code decodePath} and {@code stripBasename} in {@code lib/router/utils.ts}): React Router
 * decodes every path segment and matches case-insensitively.
 *
 * <p>The same code serves React Router 7 (Vaadin 25.1 and 25.2) and 8 (Vaadin 25.3 and later). Their matching is
 * identical, their ranking only differs for a segment that starts with a parameter and continues with text, such as
 * {@code :id.json}: version 7 scores it like a static segment, version 8 between a static and a dynamic segment.
 * Every route is therefore ranked with both scores, and the requirements of the winners of both are combined.
 *
 * <p>When several routes share the best score, React Router takes the one it ranked first, which depends on the
 * order of the routes. Hilla reorders the routes for Flow layouts, eagerly loaded views and skipped layouts before
 * React Router sees them, and {@code file-routes.json} does not record all of that, so the requirements of all of
 * them are combined.
 *
 * <p>Hilla does not add the Flow fallbacks everywhere: it skips the routes it duplicates, for instance a root
 * layout split between eagerly and lazily loaded views, and the routes within Flow layouts. Where a fallback is
 * missing, the client renders the best Hilla view instead, so the requirements of the best ranked Hilla views apply
 * even where a fallback ranks higher. A fallback itself adds no requirements: where the client renders it, the
 * content comes from Flow, which authorizes it, and a fallback below the root layout matches every path, including
 * those of REST resources and static files.
 *
 * <p>An application can also enable {@code withDeepWildcard()}, which copies a wildcard view into the layouts below
 * it: the one of the closest enclosing level, or, where Hilla has split the routes for Flow layouts before, of any
 * enclosing level. Such a copy applies wherever it ranks at least as high as the best Hilla view.
 */
final class ClientRouteMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRouteMatcher.class);

    // React Router's computeScore constants, doubled so that the 3.5 of version 8 stays an integer.
    private static final int SEGMENT = 2;
    private static final int DYNAMIC_SEGMENT = 6;
    private static final int PARTIAL_DYNAMIC_SEGMENT = 7;
    private static final int INDEX_ROUTE = 4;
    private static final int EMPTY_SEGMENT = 2;
    private static final int STATIC_SEGMENT = 20;
    private static final int SPLAT_PENALTY = -4;

    private static final String SPLAT = "*";
    private static final Pattern DYNAMIC_SEGMENT_PATTERN = Pattern.compile(":[\\w-]+");
    private static final Pattern PATH_PARAMETER = Pattern.compile("/:([\\w-]+)(\\?)?");
    private static final Pattern DOUBLE_SLASHES = Pattern.compile("[\\\\/]{2,}");
    // JavaScript's "." without the "s" flag: anything but a line terminator.
    private static final String ANY = "[^\\n\\r\\u2028\\u2029]";

    private static final Route FLOW_WILDCARD = new Route(SPLAT, false, null, Kind.FLOW_FALLBACK, null);
    private static final Route FLOW_INDEX = new Route(null, true, null, Kind.FLOW_FALLBACK, null);

    private final List<Branch> branches;
    private final String basename;

    /**
     * Creates a matcher for a client route tree.
     *
     * @param routes the client route tree, as React Router receives it
     * @param basename the router's basename, the path of the document base URI
     */
    ClientRouteMatcher(List<Route> routes, String basename) {
        this.basename = basename;
        List<Branch> collected = new ArrayList<>();
        flattenRoutes(routes, collected, List.of(), "", false, new HashMap<>());
        this.branches = List.copyOf(collected);
    }

    /**
     * Creates a matcher for the route tree that the generated {@code routes.tsx} builds from the file routes.
     *
     * @param fileRoutes the route tree of {@code file-routes.json}
     * @param basename the router's basename, the path of the document base URI
     * @return the matcher
     */
    static ClientRouteMatcher forFileRoutes(List<AvailableViewInfo> fileRoutes, String basename) {
        return new ClientRouteMatcher(clientRoutes(fileRoutes), basename);
    }

    /**
     * Returns the requirements of the client route that renders the given path.
     *
     * @param requestPath the request path, including the basename
     * @return the views whose requirements apply, the matched route and all its ancestors, or an empty optional if
     *     the client does not render a Hilla view for the path
     */
    Optional<List<AvailableViewInfo>> requirements(String requestPath) {
        String pathname = stripBasename(requestPath == null || requestPath.isEmpty() ? "/" : requestPath, basename);
        if (pathname == null) {
            return Optional.empty();
        }
        String decoded = decodePath(pathname);
        List<Branch> matching =
                branches.stream().filter(branch -> branch.matches(decoded)).toList();
        Set<Branch> candidates = new LinkedHashSet<>();
        candidates.addAll(candidates(matching, Branch::scoreV7));
        candidates.addAll(candidates(matching, Branch::scoreV8));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Set<AvailableViewInfo> views = new LinkedHashSet<>();
        candidates.forEach(branch -> views.addAll(branch.views()));
        return Optional.of(List.copyOf(views));
    }

    private static List<Branch> candidates(List<Branch> matching, ToIntFunction<Branch> score) {
        int best = matching.stream()
                .filter(branch -> branch.kind() == Kind.VIEW)
                .mapToInt(score)
                .max()
                .orElse(Integer.MIN_VALUE);
        return matching.stream()
                .filter(branch -> branch.kind() == Kind.VIEW
                        ? score.applyAsInt(branch) == best
                        : branch.kind() == Kind.WILDCARD_COPY && score.applyAsInt(branch) >= best)
                .toList();
    }

    /**
     * Builds the client route tree like {@code RouterConfigurationBuilder} does for the generated
     * {@code routes.tsx}, with {@code fileRouteTransformer} and {@code createFallbackTransformer}, and adds the
     * routes that {@code mergeDeepWildcard} adds when an application enables it.
     *
     * @param fileRoutes the route tree of {@code file-routes.json}
     * @return the client route tree
     */
    static List<Route> clientRoutes(List<AvailableViewInfo> fileRoutes) {
        // Applied after mergeLayout, which splits the routes between Flow layouts and the client, mergeDeepWildcard
        // can copy the wildcard view of any enclosing level into a layout, even into one with a wildcard view.
        boolean flowLayouts = hasFlowLayout(fileRoutes);
        List<Route> routes = toRoutes(fileRoutes, List.of(), flowLayouts);
        // At the top level, a fallback is merged with an existing route of the same path that has no children.
        if (routes.stream().noneMatch(route -> SPLAT.equals(route.path()) && route.children() == null)) {
            routes.add(FLOW_WILDCARD);
        }
        if (routes.stream().noneMatch(route -> isEmpty(route.path()) && route.children() == null)) {
            routes.add(FLOW_INDEX);
        }
        return routes;
    }

    private static boolean hasFlowLayout(List<AvailableViewInfo> views) {
        return views.stream()
                .anyMatch(view -> view != null
                        && (view.flowLayout() || (view.children() != null && hasFlowLayout(view.children()))));
    }

    private static List<Route> toRoutes(
            List<AvailableViewInfo> views, List<AvailableViewInfo> enclosingWildcards, boolean flowLayouts) {
        // The wildcard views of this level, then those of the enclosing levels, the closest first.
        List<AvailableViewInfo> wildcards = new ArrayList<>();
        views.stream()
                .filter(view -> view != null && SPLAT.equals(view.route()))
                .forEach(wildcards::add);
        wildcards.addAll(enclosingWildcards);
        List<Route> routes = new ArrayList<>();
        for (AvailableViewInfo view : views) {
            if (view == null) {
                continue;
            }
            if (view.children() == null) {
                // A view with an empty path becomes an index route.
                boolean index = "".equals(view.route());
                routes.add(new Route(index ? null : view.route(), index, view, Kind.VIEW, null));
                continue;
            }
            List<Route> children = toRoutes(view.children(), wildcards, flowLayouts);
            boolean hasWildcard = children.stream().anyMatch(child -> SPLAT.equals(child.path()));
            boolean hasIndexOrOptional = children.stream()
                    .anyMatch(child -> child.index()
                            || (child.path() != null && child.path().contains("?")));
            if (flowLayouts) {
                wildcards.forEach(
                        wildcard -> children.add(new Route(SPLAT, false, wildcard, Kind.WILDCARD_COPY, null)));
            } else if (!hasWildcard && !wildcards.isEmpty()) {
                children.add(new Route(SPLAT, false, wildcards.get(0), Kind.WILDCARD_COPY, null));
            }
            if (hasWildcard) {
                children.add(FLOW_INDEX);
            } else if (hasIndexOrOptional) {
                children.add(FLOW_WILDCARD);
            } else {
                children.add(FLOW_WILDCARD);
                children.add(FLOW_INDEX);
            }
            routes.add(new Route(view.route(), false, view, Kind.VIEW, children));
        }
        return routes;
    }

    private static void flattenRoutes(
            List<Route> routes,
            List<Branch> branches,
            List<Level> parents,
            String parentPath,
            boolean hasParentOptionalSegments,
            Map<String, CompiledPath> compiled) {
        for (Route route : routes) {
            String path = route.path();
            if (path == null || path.isEmpty() || !path.contains("?")) {
                Parent parent = new Parent(parents, parentPath, hasParentOptionalSegments);
                flattenRoute(route, path == null ? "" : path, parent, branches, compiled);
            } else {
                for (String exploded : explodeOptionalSegments(path)) {
                    flattenRoute(route, exploded, new Parent(parents, parentPath, true), branches, compiled);
                }
            }
        }
    }

    private static void flattenRoute(
            Route route,
            String relativePath,
            Parent parent,
            List<Branch> branches,
            Map<String, CompiledPath> compiled) {
        String relative = relativePath;
        if (relative.startsWith("/")) {
            // An absolute child path is used as is and must start with the path of its parents.
            if (!relative.startsWith(parent.path())) {
                if (!parent.hasOptionalSegments()) {
                    // React Router fails to create the router, the client renders no route at all.
                    LOGGER.warn(
                            "Absolute route path \"{}\" nested under path \"{}\" is not valid; it is ignored",
                            relative,
                            parent.path());
                }
                return;
            }
            relative = relative.substring(parent.path().length());
        }
        String path = joinPaths(parent.path(), relative);
        List<Level> levels = new ArrayList<>(parent.levels());
        levels.add(new Level(relative, route));
        if (route.children() != null && !route.children().isEmpty()) {
            flattenRoutes(route.children(), branches, levels, path, parent.hasOptionalSegments(), compiled);
        }
        if (route.path() == null && !route.index()) {
            return;
        }
        try {
            List<CompiledPath> matchers = new ArrayList<>();
            for (int i = 0; i < levels.size(); i++) {
                boolean end = i == levels.size() - 1;
                String levelPath = levels.get(i).relativePath();
                matchers.add(compiled.computeIfAbsent(end + levelPath, ignored -> compilePath(levelPath, end)));
            }
            branches.add(new Branch(
                    computeScore(path, route.index(), false),
                    computeScore(path, route.index(), true),
                    List.copyOf(levels),
                    List.copyOf(matchers)));
        } catch (PatternSyntaxException exception) {
            // React Router fails to create the router as well.
            LOGGER.warn("Route path \"{}\" is not a valid pattern; it is ignored", path, exception);
        }
    }

    /**
     * Ports React Router's {@code explodeOptionalSegments}: a path with optional segments, which end with {@code ?},
     * becomes one path for each combination of present and absent optional segments.
     *
     * @param path the route path
     * @return the paths without optional segments
     */
    static List<String> explodeOptionalSegments(String path) {
        String[] segments = path.split("/", -1);
        String first = segments[0];
        boolean optional = first.endsWith("?");
        String required = optional ? first.substring(0, first.length() - 1) : first;
        if (segments.length == 1) {
            return optional ? List.of(required, "") : List.of(required);
        }
        List<String> rest = explodeOptionalSegments(String.join("/", Arrays.copyOfRange(segments, 1, segments.length)));
        List<String> result = new ArrayList<>();
        rest.forEach(subpath -> result.add(subpath.isEmpty() ? required : required + "/" + subpath));
        if (optional) {
            result.addAll(rest);
        }
        return result.stream()
                .map(exploded -> path.startsWith("/") && exploded.isEmpty() ? "/" : exploded)
                .toList();
    }

    /**
     * Ports React Router's {@code computeScore}, doubled.
     *
     * @param path the joined path of a branch
     * @param index whether the branch ends with an index route
     * @param version8 whether to score like React Router 8, which scores a segment that starts with a parameter and
     *     continues with text lower than a static segment
     * @return the doubled score
     */
    static int computeScore(String path, boolean index, boolean version8) {
        String[] segments = path.split("/", -1);
        int score = segments.length * SEGMENT;
        if (Arrays.asList(segments).contains(SPLAT)) {
            score += SPLAT_PENALTY;
        }
        if (index) {
            score += INDEX_ROUTE;
        }
        for (String segment : segments) {
            if (SPLAT.equals(segment)) {
                continue;
            }
            Matcher dynamic = DYNAMIC_SEGMENT_PATTERN.matcher(segment);
            if (dynamic.matches()) {
                score += DYNAMIC_SEGMENT;
            } else if (version8 && dynamic.lookingAt()) {
                score += PARTIAL_DYNAMIC_SEGMENT;
            } else {
                score += segment.isEmpty() ? EMPTY_SEGMENT : STATIC_SEGMENT;
            }
        }
        return score;
    }

    /**
     * Ports React Router's {@code compilePath} to a Java pattern with the semantics of the JavaScript one, which is
     * case-insensitive and anchored at the end of the input.
     *
     * @param path the relative path of a route
     * @param end whether the route is the last of its branch
     * @return the compiled path
     */
    static CompiledPath compilePath(String path, boolean end) {
        String source = "/" + stripLeadingSlashes(stripTrailingSplat(path));
        StringBuilder regex = new StringBuilder("^");
        int groups = 0;
        int literalStart = 0;
        Matcher parameter = PATH_PARAMETER.matcher(source);
        while (parameter.find()) {
            appendLiteral(regex, source, literalStart, parameter.start());
            groups++;
            if (parameter.group(2) == null) {
                regex.append("/([^/]+)");
            } else if (parameter.end() < source.length() && source.charAt(parameter.end()) != '/') {
                // An optional parameter followed by text, such as ":lang?.xml", may be empty.
                regex.append("/([^/]*)");
            } else {
                regex.append("(?:/([^/]*))?");
            }
            literalStart = parameter.end();
        }
        appendLiteral(regex, source, literalStart, source.length());
        // React Router's replacement of optional static segments is left out: after explodeOptionalSegments, no
        // segment of a route path ends with "?".
        int splatGroup = 0;
        if (path.endsWith(SPLAT)) {
            splatGroup = groups + 1;
            regex.append(path.equals(SPLAT) || path.equals("/*") ? "(" + ANY + "*)\\z" : "(?:/(" + ANY + "+)|/*)\\z");
        } else if (end) {
            regex.append("/*\\z");
        } else if (!path.isEmpty() && !path.equals("/")) {
            regex.append("(?=/|\\z)");
        }
        return new CompiledPath(Pattern.compile(regex.toString()), splatGroup);
    }

    private static String stripTrailingSplat(String path) {
        int end = path.length();
        if (path.endsWith(SPLAT)) {
            end--;
        }
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static String stripLeadingSlashes(String path) {
        int start = 0;
        while (start < path.length() && path.charAt(start) == '/') {
            start++;
        }
        return path.substring(start);
    }

    private static void appendLiteral(StringBuilder regex, String source, int start, int end) {
        int index = start;
        while (index < end) {
            int codePoint = source.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '?') {
                // React Router does not escape "?", so it makes the preceding character optional.
                regex.append('?');
            } else if (codePoint > Character.MAX_VALUE || Character.isSurrogate((char) codePoint)) {
                appendCharacter(regex, codePoint);
            } else {
                char[] equivalents = JavaScriptCase.equivalents((char) codePoint);
                if (equivalents.length == 1) {
                    appendCharacter(regex, codePoint);
                } else {
                    regex.append('[');
                    for (char equivalent : equivalents) {
                        appendCharacter(regex, equivalent);
                    }
                    regex.append(']');
                }
            }
        }
    }

    private static void appendCharacter(StringBuilder regex, int codePoint) {
        regex.append("\\x{").append(Integer.toHexString(codePoint)).append('}');
    }

    /**
     * Ports React Router's {@code decodePath}: decodes every segment like {@code decodeURIComponent}, keeping an
     * encoded slash encoded, or returns the path as is if a segment cannot be decoded.
     *
     * @param path the path
     * @return the decoded path
     */
    static String decodePath(String path) {
        String[] segments = path.split("/", -1);
        try {
            for (int i = 0; i < segments.length; i++) {
                segments[i] = decodeUriComponent(segments[i]).replace("/", "%2F");
            }
        } catch (IllegalArgumentException malformed) {
            return path;
        }
        return String.join("/", segments);
    }

    private static String decodeUriComponent(String value) {
        int index = value.indexOf('%');
        if (index < 0) {
            return value;
        }
        StringBuilder decoded = new StringBuilder(value.length()).append(value, 0, index);
        while (index < value.length()) {
            if (value.charAt(index) != '%') {
                decoded.append(value.charAt(index++));
                continue;
            }
            ByteBuffer bytes = ByteBuffer.allocate(value.length());
            while (index < value.length() && value.charAt(index) == '%') {
                if (index + 2 >= value.length()) {
                    throw new IllegalArgumentException("Incomplete escape sequence");
                }
                bytes.put((byte) (hexDigit(value.charAt(index + 1)) << 4 | hexDigit(value.charAt(index + 2))));
                index += 3;
            }
            bytes.flip();
            try {
                decoded.append(StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(bytes));
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("Invalid UTF-8 escape sequence", exception);
            }
        }
        return decoded.toString();
    }

    private static int hexDigit(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        if (character >= 'a' && character <= 'f') {
            return character - 'a' + 10;
        }
        if (character >= 'A' && character <= 'F') {
            return character - 'A' + 10;
        }
        throw new IllegalArgumentException("Invalid escape sequence");
    }

    /**
     * Ports React Router's {@code stripBasename}, which compares the basename case-insensitively.
     *
     * @param pathname the request path
     * @param basename the basename
     * @return the path below the basename, or {@code null} if the path is not below it
     */
    static String stripBasename(String pathname, String basename) {
        if ("/".equals(basename)) {
            return pathname;
        }
        if (!pathname.toLowerCase(Locale.ROOT).startsWith(basename.toLowerCase(Locale.ROOT))) {
            return null;
        }
        int start = basename.endsWith("/") ? basename.length() - 1 : basename.length();
        if (start < pathname.length() && pathname.charAt(start) != '/') {
            return null;
        }
        String rest = start < pathname.length() ? pathname.substring(start) : "";
        return rest.isEmpty() ? "/" : rest;
    }

    private static String joinPaths(String first, String second) {
        return DOUBLE_SLASHES.matcher(first + "/" + second).replaceAll("/");
    }

    private static String removeTrailingSlash(String path) {
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }

    private static boolean isEmpty(String path) {
        return path == null || path.isEmpty();
    }

    /**
     * A client route, as React Router receives it.
     *
     * @param path the path, {@code null} for an index route or a layout without a path
     * @param index whether the route is an index route
     * @param view the file route whose requirements apply, {@code null} for a Flow fallback
     * @param kind the kind of the route
     * @param children the child routes, {@code null} for a route without children
     */
    record Route(String path, boolean index, AvailableViewInfo view, Kind kind, List<Route> children) {}

    /**
     * The kind of a client route.
     */
    enum Kind {
        /** A file route. */
        VIEW,
        /** A fallback that renders Flow, the server-side router, where Hilla adds it. */
        FLOW_FALLBACK,
        /** A copy of a wildcard view, where an application enables {@code withDeepWildcard()}. */
        WILDCARD_COPY
    }

    /**
     * A compiled route path.
     *
     * @param pattern the pattern
     * @param splatGroup the group of the splat parameter, {@code 0} if there is none
     */
    record CompiledPath(Pattern pattern, int splatGroup) {}

    private record Parent(List<Level> levels, String path, boolean hasOptionalSegments) {}

    private record Level(String relativePath, Route route) {}

    /**
     * A route with its ancestors, each with its path relative to its parent.
     */
    private record Branch(int scoreV7, int scoreV8, List<Level> levels, List<CompiledPath> matchers) {

        Kind kind() {
            return levels.get(levels.size() - 1).route().kind();
        }

        List<AvailableViewInfo> views() {
            return levels.stream()
                    .map(level -> level.route().view())
                    .filter(view -> view != null)
                    .toList();
        }

        // Ports React Router's matchRouteBranch.
        boolean matches(String pathname) {
            String matchedPathname = "/";
            for (CompiledPath compiled : matchers) {
                String remaining = "/".equals(matchedPathname) ? pathname : slice(pathname, matchedPathname.length());
                Matcher matcher = compiled.pattern().matcher(remaining);
                if (!matcher.lookingAt()) {
                    return false;
                }
                String matched = matcher.group();
                String pathnameBase = removeTrailingSlash(matched);
                if (compiled.splatGroup() > 0) {
                    String splat = matcher.group(compiled.splatGroup());
                    int splatLength = splat == null ? 0 : splat.length();
                    pathnameBase = removeTrailingSlash(matched.substring(0, matched.length() - splatLength));
                }
                if (!"/".equals(pathnameBase)) {
                    matchedPathname = joinPaths(matchedPathname, pathnameBase);
                }
            }
            return true;
        }

        private static String slice(String pathname, int start) {
            return start < pathname.length() ? pathname.substring(start) : "/";
        }
    }

    /**
     * Case-insensitive comparison of a JavaScript regular expression without the {@code u} flag, as React Router
     * uses it: two characters are equal if their upper case forms are, unless the upper case form of a character has
     * more than one character or is ASCII while the character is not.
     */
    static final class JavaScriptCase {

        private static final Map<Character, char[]> EQUIVALENTS = new ConcurrentHashMap<>();

        private JavaScriptCase() {}

        static char canonicalize(char character) {
            String upper = String.valueOf(character).toUpperCase(Locale.ROOT);
            if (upper.length() != 1) {
                return character;
            }
            char canonical = upper.charAt(0);
            return character >= 128 && canonical < 128 ? character : canonical;
        }

        static char[] equivalents(char character) {
            if (character < 128) {
                char lower = Character.toLowerCase(character);
                char upper = Character.toUpperCase(character);
                return lower == upper ? new char[] {character} : new char[] {lower, upper};
            }
            // From 128 on, neither a character nor its canonical form is ASCII, so ASCII is not searched.
            return EQUIVALENTS.computeIfAbsent(character, ignored -> {
                char canonical = canonicalize(character);
                StringBuilder equivalents = new StringBuilder();
                for (int candidate = 128; candidate <= Character.MAX_VALUE; candidate++) {
                    if (!Character.isSurrogate((char) candidate) && canonicalize((char) candidate) == canonical) {
                        equivalents.append((char) candidate);
                    }
                }
                return equivalents.toString().toCharArray();
            });
        }
    }
}
