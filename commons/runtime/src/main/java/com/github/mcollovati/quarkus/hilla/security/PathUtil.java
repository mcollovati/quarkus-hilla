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
package com.github.mcollovati.quarkus.hilla.security;

public class PathUtil {

    /**
     * Prepends to the given {@code path} with the servlet path prefix from
     * input url mapping.
     *
     * A {@literal null} path is treated as empty string; the same applies for
     * url mapping.
     *
     * @return the path with prepended url mapping.
     */
    static String applyUrlMapping(String urlMapping, String path) {
        if (urlMapping == null) {
            urlMapping = "";
        } else {
            // remove potential / or /* at the end of the mapping
            urlMapping = urlMapping.replaceFirst("/\\*?$", "");
        }
        if (path == null) {
            path = "";
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return urlMapping + "/" + path;
    }

    static String ensureSlashBegin(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    public static String normalizeWildcard(String path) {
        if (path.endsWith("/") || path.endsWith("/**")) {
            path = path.replaceFirst("/(\\*\\*)?$", "/*");
        }
        return path;
    }
}
