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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import com.vaadin.flow.internal.FrontendUtils;
import com.vaadin.flow.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FileRoutesManifestClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileRoutesManifestClassifier.class);
    private static final Pattern WITH_FILE_ROUTES_CALL = Pattern.compile("withFileRoutes\\s*\\(");

    private FileRoutesManifestClassifier() {}

    static boolean isManifestExpected(File frontendDirectory, Optional<Boolean> reactEnabledOverride) {
        boolean reactEnabled =
                reactEnabledOverride.orElseGet(() -> FrontendUtils.isReactRouterRequired(frontendDirectory));
        if (!reactEnabled) {
            return false;
        }

        Path routesTsx = frontendDirectory.toPath().resolve("routes.tsx");
        Path routesTs = frontendDirectory.toPath().resolve("routes.ts");
        Path customRoutes =
                Files.isRegularFile(routesTsx) ? routesTsx : Files.isRegularFile(routesTs) ? routesTs : null;
        if (customRoutes == null) {
            return FrontendUtils.isHillaViewsUsed(frontendDirectory);
        }

        try {
            String routes = StringUtil.removeComments(Files.readString(customRoutes));
            return WITH_FILE_ROUTES_CALL.matcher(routes).find();
        } catch (IOException exception) {
            LOGGER.warn(
                    "Cannot inspect custom React router {}; expecting generated file routes", customRoutes, exception);
            return true;
        }
    }
}
