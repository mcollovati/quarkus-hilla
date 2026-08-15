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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Matches server request paths against Hilla file-router route patterns using React Router 8 branch-ranking
 * semantics.
 *
 * <p>This is a versioned compatibility boundary, not a generic URL matcher. React Router 7, used by the 25.1 and
 * 25.2 branches, ranks partial parameter segments differently. Revalidate the scoring constants and conformance
 * tests whenever the managed React Router version changes. Unlike React Router, which resolves an equal-ranking
 * sibling tie by declaration order, this matcher returns all tied routes so authorization remains conservative and
 * independent of generated route order.
 */
final class RoutePatternMatcher {

    private static final Pattern DYNAMIC_SEGMENT = Pattern.compile("^:([\\w-]+)(\\?)?(.*)$");
    private static final Pattern OPTIONAL_STATIC_SEGMENT = Pattern.compile("^[\\w-]+\\?$");

    /*
     * React Router 8 branch-ranking weights, multiplied by two to represent its
     * 3.5 partial-parameter value as an integer. Each consumed segment includes
     * the branch base score. Higher scores win.
     */
    private static final int NO_MATCH_SCORE = Integer.MIN_VALUE;
    private static final int WILDCARD_SCORE = -2;
    private static final int EMPTY_ROUTE_SCORE = 4;
    private static final int INDEX_ROUTE_SCORE = 4;
    private static final int DYNAMIC_SEGMENT_SCORE = 8;
    private static final int PARTIAL_DYNAMIC_SEGMENT_SCORE = 9;
    private static final int STATIC_SEGMENT_SCORE = 22;

    private RoutePatternMatcher() {}

    static <T> CompiledRoute<T> compile(String route, T target) {
        return compile(route, false, target);
    }

    static <T> CompiledRoute<T> compile(String route, boolean index, T target) {
        return new CompiledRoute<>(route, routeSegments(route), index, target);
    }

    static <T> List<CompiledRoute<T>> bestMatches(List<CompiledRoute<T>> availableRoutes, String path) {
        List<CompiledRoute<T>> matches = new ArrayList<>(bestMatchesForPath(availableRoutes, path));
        String clientPath = clientRouterPath(path);
        if (!path.equals(clientPath)) {
            Set<CompiledRoute<T>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            seen.addAll(matches);
            for (CompiledRoute<T> route : bestMatchesForPath(availableRoutes, clientPath)) {
                if (seen.add(route)) {
                    matches.add(route);
                }
            }
        }
        return List.copyOf(matches);
    }

    private static <T> List<CompiledRoute<T>> bestMatchesForPath(List<CompiledRoute<T>> availableRoutes, String path) {
        List<CompiledRoute<T>> bestMatches = new ArrayList<>();
        List<String> pathSegments = segments(path);
        int bestScore = NO_MATCH_SCORE;
        for (CompiledRoute<T> route : availableRoutes) {
            int score = matchScore(route.segments(), pathSegments, 0, 0);
            if (score == NO_MATCH_SCORE) {
                continue;
            }
            if (route.index()) {
                score += INDEX_ROUTE_SCORE;
            }
            if (score > bestScore) {
                bestMatches.clear();
                bestScore = score;
            }
            // React Router resolves a remaining tie by sibling declaration
            // order. Return every tied branch so authorization cannot depend
            // on generated-file ordering; caller requires all to allow.
            if (score == bestScore) {
                bestMatches.add(route);
            }
        }
        return List.copyOf(bestMatches);
    }

    private static int matchScore(
            List<SegmentPattern> routeSegments, List<String> pathSegments, int routeIndex, int pathIndex) {
        if (routeIndex == routeSegments.size()) {
            if (pathIndex != pathSegments.size()) {
                return NO_MATCH_SCORE;
            }
            return pathSegments.isEmpty() ? EMPTY_ROUTE_SCORE : 0;
        }

        SegmentPattern segmentPattern = routeSegments.get(routeIndex);
        if (segmentPattern.type() == SegmentType.WILDCARD) {
            if (routeIndex == routeSegments.size() - 1) {
                return WILDCARD_SCORE;
            }
            int matched =
                    remainingSegmentsCanBeOmitted(routeSegments, routeIndex + 1) ? WILDCARD_SCORE : NO_MATCH_SCORE;
            if (pathIndex < pathSegments.size() && "*".equals(pathSegments.get(pathIndex))) {
                int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
                if (remaining != NO_MATCH_SCORE) {
                    matched = Math.max(matched, remaining + WILDCARD_SCORE);
                }
            }
            return matched;
        }

        int skipped = segmentPattern.optional() && segmentPattern.canBeOmitted()
                ? matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex)
                : NO_MATCH_SCORE;
        if (pathIndex == pathSegments.size()) {
            return skipped;
        }

        String pathSegment = pathSegments.get(pathIndex);
        int segmentScore = segmentScore(segmentPattern, pathSegment);
        if (segmentScore == NO_MATCH_SCORE) {
            return skipped;
        }
        int remaining = matchScore(routeSegments, pathSegments, routeIndex + 1, pathIndex + 1);
        return remaining == NO_MATCH_SCORE ? skipped : Math.max(skipped, remaining + segmentScore);
    }

    private static int segmentScore(SegmentPattern pattern, String pathSegment) {
        if (pattern.type() == SegmentType.DYNAMIC && pattern.matchesDynamic(pathSegment)) {
            return pattern.value().isEmpty() ? DYNAMIC_SEGMENT_SCORE : PARTIAL_DYNAMIC_SEGMENT_SCORE;
        }
        if (pattern.type() == SegmentType.STATIC && pattern.value().equalsIgnoreCase(pathSegment)) {
            return STATIC_SEGMENT_SCORE;
        }
        return NO_MATCH_SCORE;
    }

    private static boolean remainingSegmentsCanBeOmitted(List<SegmentPattern> routeSegments, int routeIndex) {
        for (int index = routeIndex; index < routeSegments.size(); index++) {
            SegmentPattern remaining = routeSegments.get(index);
            if (!remaining.optional() || !remaining.canBeOmitted()) {
                return false;
            }
        }
        return true;
    }

    private static List<SegmentPattern> routeSegments(String route) {
        String normalizedRoute = route;
        // React Router treats any trailing splat without a slash as if the
        // missing slash were present, including patterns such as :id*.
        if (normalizedRoute != null
                && normalizedRoute.endsWith("*")
                && !"*".equals(normalizedRoute)
                && !normalizedRoute.endsWith("/*")) {
            normalizedRoute = normalizedRoute.substring(0, normalizedRoute.length() - 1) + "/*";
        }
        List<String> segments = segments(normalizedRoute);
        List<SegmentPattern> patterns = new ArrayList<>(segments.size());
        for (String segment : segments) {
            patterns.add(segmentPattern(segment));
        }
        return List.copyOf(patterns);
    }

    private static SegmentPattern segmentPattern(String routeSegment) {
        if ("*".equals(routeSegment)) {
            return new SegmentPattern(SegmentType.WILDCARD, "", false);
        }
        Matcher dynamic = DYNAMIC_SEGMENT.matcher(routeSegment);
        if (dynamic.matches()) {
            return new SegmentPattern(SegmentType.DYNAMIC, dynamic.group(3), dynamic.group(2) != null);
        }
        if (OPTIONAL_STATIC_SEGMENT.matcher(routeSegment).matches()) {
            return new SegmentPattern(SegmentType.STATIC, routeSegment.substring(0, routeSegment.length() - 1), true);
        }
        return new SegmentPattern(SegmentType.STATIC, routeSegment, false);
    }

    private static List<String> segments(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return List.of();
        }
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') {
            start++;
        }
        while (end > start && path.charAt(end - 1) == '/') {
            end--;
        }
        return start == end
                ? List.of()
                : Arrays.asList(path.substring(start, end).split("/", -1));
    }

    private static String clientRouterPath(String path) {
        if (path.indexOf('%') < 0) {
            return path;
        }
        return String.join(
                "/",
                Arrays.stream(path.split("/", -1))
                        .map(RoutePatternMatcher::decodeClientRouterSegment)
                        .toList());
    }

    private static String decodeClientRouterSegment(String segment) {
        try {
            return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8)
                    .replace("/", "%2F");
        } catch (IllegalArgumentException exception) {
            return segment;
        }
    }

    record CompiledRoute<T>(String path, List<SegmentPattern> segments, boolean index, T target) {}

    private enum SegmentType {
        STATIC,
        DYNAMIC,
        WILDCARD
    }

    private record SegmentPattern(SegmentType type, String value, boolean optional) {

        boolean canBeOmitted() {
            return type == SegmentType.STATIC || value.isEmpty();
        }

        boolean matchesDynamic(String pathSegment) {
            if (!endsWithIgnoreCase(pathSegment, value)) {
                return false;
            }
            int parameterLength = pathSegment.length() - value.length();
            return optional ? parameterLength >= 0 : parameterLength > 0;
        }

        private static boolean endsWithIgnoreCase(String value, String suffix) {
            return value.regionMatches(true, value.length() - suffix.length(), suffix, 0, suffix.length());
        }
    }
}
