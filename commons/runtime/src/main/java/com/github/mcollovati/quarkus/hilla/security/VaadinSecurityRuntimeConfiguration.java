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

import java.util.Map;

import io.quarkus.vertx.http.runtime.PolicyConfig;
import io.quarkus.vertx.http.runtime.PolicyMappingConfig;

/**
 * Effective runtime security configuration shared by navigation enforcement
 * and startup diagnostics.
 *
 * @param permissions configured HTTP permission mappings
 * @param rolePolicies configured role policies
 * @param rootPath Quarkus HTTP root path
 * @param annotationConfigMismatch annotation/configuration diagnostic mode
 */
public record VaadinSecurityRuntimeConfiguration(
        Map<String, PolicyMappingConfig> permissions,
        Map<String, PolicyConfig> rolePolicies,
        String rootPath,
        VaadinSecurityRuntimeConfig.AnnotationConfigMismatch annotationConfigMismatch) {

    public VaadinSecurityRuntimeConfiguration {
        permissions = Map.copyOf(permissions);
        rolePolicies = Map.copyOf(rolePolicies);
        rootPath = normalizeRootPath(rootPath);
    }

    String resolveApplicationPath(String path) {
        if (path == null) {
            return null;
        }
        String applicationPath = PathUtil.ensureSlashBegin(path);
        return "/".equals(rootPath) ? applicationPath : rootPath + applicationPath;
    }

    String relativizeApplicationPath(String path) {
        if (path == null || "/".equals(rootPath)) {
            return path;
        }
        if (path.equals(rootPath)) {
            return "/";
        }
        return path.startsWith(rootPath + "/") ? path.substring(rootPath.length()) : path;
    }

    private static String normalizeRootPath(String rootPath) {
        if (rootPath == null || rootPath.isBlank() || "/".equals(rootPath)) {
            return "/";
        }
        String normalized = PathUtil.ensureSlashBegin(rootPath.trim());
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
