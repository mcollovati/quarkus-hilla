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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.vaadin.flow.server.menu.AvailableViewInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches Hilla client routes using React Router's path matching and score ranking rules.
 * All equally ranked matching views are returned so callers can combine their access requirements strictly.
 * Layout and ancestor requirements are not applied.
 */
final class ClientRouteMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRouteMatcher.class);

    // React Router 8's computeScore constants, doubled to keep the partial dynamic score integral.
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

    private final List<Branch> branches;
    private final String basename;

    /**
     * Creates a matcher from a flattened route map.
     *
     * @param registeredRoutes the flattened route paths and their views
     * @param basename the path of the router's document base URI
     */
    ClientRouteMatcher(Map<String, AvailableViewInfo> registeredRoutes, String basename) {
        this.basename = basename;
        List<Branch> collected = new ArrayList<>();
        registeredRoutes.forEach((path, view) -> {
            Route route = new Route(PathUtil.ensureSlashBegin(path), false, view, null);
            flattenRoutes(List.of(route), collected, List.of(), "", false, new HashMap<>());
        });
        collected.sort(Comparator.comparingInt(Branch::score).reversed());
        branches = List.copyOf(collected);
    }

    private ClientRouteMatcher(List<Route> routes, String basename) {
        this.basename = basename;
        List<Branch> collected = new ArrayList<>();
        flattenRoutes(routes, collected, List.of(), "", false, new HashMap<>());
        collected.sort(Comparator.comparingInt(Branch::score).reversed());
        branches = List.copyOf(collected);
    }

    /**
     * Creates a matcher from file routes, using the tree only for paths and score ranking.
     *
     * @param fileRoutes the file route tree
     * @param basename the path of the router's document base URI
     * @return the matcher
     */
    static ClientRouteMatcher forFileRoutes(List<AvailableViewInfo> fileRoutes, String basename) {
        return new ClientRouteMatcher(toRoutes(fileRoutes), basename);
    }

    /**
     * Finds all matching views with the highest score before any principal-specific filtering.
     * Equally ranked candidates must be combined strictly (fail closed), because the client may reorder them
     * during optional-segment expansion or route transformations. Their order must never weaken access requirements.
     *
     * @param requestPath the request path, including the basename
     * @return all highest-scoring matching views, or an empty optional if no route matches
     */
    Optional<List<AvailableViewInfo>> match(String requestPath) {
        String pathname = stripBasename(requestPath == null || requestPath.isEmpty() ? "/" : requestPath, basename);
        if (pathname == null) {
            return Optional.empty();
        }
        String decoded = decodePath(pathname);
        Set<AvailableViewInfo> views = new LinkedHashSet<>();
        int best = Integer.MIN_VALUE;
        for (Branch branch : branches) {
            if (branch.score() < best) {
                break;
            }
            if (branch.matches(decoded)) {
                best = branch.score();
                views.add(branch.view());
            }
        }
        return views.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(views));
    }

    private static List<Route> toRoutes(List<AvailableViewInfo> views) {
        List<Route> routes = new ArrayList<>();
        for (AvailableViewInfo view : views) {
            if (view == null) {
                continue;
            }
            boolean index = "".equals(view.route()) && view.children() == null;
            routes.add(new Route(
                    index ? null : view.route(),
                    index,
                    view,
                    view.children() == null ? null : toRoutes(view.children())));
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
        levels.add(new Level(relative));
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
            branches.add(new Branch(computeScore(path, route.index()), route.view(), List.copyOf(matchers)));
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
     * Ports React Router 8's {@code computeScore}, doubled.
     *
     * @param path the flattened route path
     * @param index whether the route is an index route
     * @return the doubled score
     */
    static int computeScore(String path, boolean index) {
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
            } else if (dynamic.lookingAt()) {
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
     * @param path the flattened route path
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

    private record Route(String path, boolean index, AvailableViewInfo view, List<Route> children) {}

    private record CompiledPath(Pattern pattern, int splatGroup) {}

    private record Parent(List<Level> levels, String path, boolean hasOptionalSegments) {}

    private record Level(String relativePath) {}

    private record Branch(int score, AvailableViewInfo view, List<CompiledPath> matchers) {

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
