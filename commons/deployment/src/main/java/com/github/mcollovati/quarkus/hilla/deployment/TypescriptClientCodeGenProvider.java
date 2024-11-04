/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Scanner;

import com.vaadin.hilla.Endpoint;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;

public class TypescriptClientCodeGenProvider implements CodeGenProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypescriptClientCodeGenProvider.class);

    static final String FRONTEND_FOLDER_NAME = "frontend";
    private Path frontendFolder;

    @Override
    public String providerId() {
        return "quarkus-hilla-connect-client";
    }

    @Override
    public String inputDirectory() {
        return FRONTEND_FOLDER_NAME;
    }

    @Override
    public void init(ApplicationModel model, Map<String, String> properties) {
        if (model.getApplicationModule() == null) {
            LOGGER.info("ApplicationModule is null");
            return;
        }
        File moduleDir = model.getApplicationModule().getModuleDir();
        Path legacyFrontendFolder = moduleDir.toPath().resolve(FRONTEND_FOLDER_NAME);
        Path frontendDir = moduleDir.toPath().resolve(Path.of("src", "main", FRONTEND_FOLDER_NAME));

        if (Files.isDirectory(frontendDir)) {
            this.frontendFolder = frontendDir;
            if (Files.isDirectory(legacyFrontendFolder)) {
                LOGGER.warn(
                        "Using frontend folder {}, but also found legacy frontend folder {}. "
                                + "Consider removing the legacy folder.",
                        frontendDir,
                        legacyFrontendFolder);
            } else {
                LOGGER.debug("Using frontend folder {}", frontendDir);
            }
        } else if (Files.isDirectory(legacyFrontendFolder)) {
            this.frontendFolder = legacyFrontendFolder;
            LOGGER.debug("Using legacy frontend folder {}", legacyFrontendFolder);
        } else {
            LOGGER.debug("Frontend folder not found");
        }
    }

    @Override
    public Path getInputDirectory() {
        return frontendFolder;
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {
        if (!Files.isDirectory(sourceDir)) {
            return false;
        }
        String prefix = computeConnectClientPrefix(config);
        boolean defaultPrefix = "connect".equals(prefix);
        Path customClient = sourceDir.resolve("connect-client.ts");
        if (Files.exists(customClient)) {
            try {
                String content = Files.readString(customClient);
                if (!content.contains("const client = new ConnectClient({prefix: '" + prefix + "'});")) {
                    LOGGER.debug(
                            "Custom connect-client.ts detected ({}), but prefix does not match configuration {}.",
                            customClient,
                            prefix);
                    return true;
                }
            } catch (IOException e) {
                LOGGER.debug("Custom connect-client.ts detected ({}), but cannot read content.", customClient);
                return false;
            }
            LOGGER.debug("Custom connect-client.ts detected ({}) with expected prefix {}.", customClient, prefix);
            return false;
        } else if (!defaultPrefix) {
            LOGGER.debug("Custom prefix {} detected, connect-client.ts to be created in {}.", prefix, customClient);
        }
        return !defaultPrefix;
    }

    @Override
    public boolean trigger(CodeGenContext context) {
        String prefix = computeConnectClientPrefix(context.config());
        boolean defaultPrefix = "connect".equals(prefix);
        Path customClient = context.inputDir().resolve("connect-client.ts");
        if (Files.exists(customClient)) {
            String content = null;
            try {
                content = Files.readString(customClient);
            } catch (IOException e) {
                LOGGER.debug(
                        "Cannot read content of custom connect-client.ts ({}). File will not be overwritten.",
                        customClient,
                        e);
            }
            if (content != null) {
                content = content.replaceFirst(
                        "(const client = new ConnectClient\\(\\{prefix:\\s*')[^']+('}\\);)", "$1" + prefix + "$2");
                try {
                    Files.writeString(customClient, content);
                    LOGGER.debug(
                            "Prefix in custom connect-client.ts ({}) replaced with new value {}", customClient, prefix);
                    return true;
                } catch (IOException e) {
                    LOGGER.warn("Cannot write content of custom connect-client.ts ({}).", customClient, e);
                }
            }
        } else if (!defaultPrefix) {
            return writeConnectClient(prefix, customClient);
        }
        return false;
    }

    private static String computeConnectClientPrefix(Config config) {
        String prefix = config.getValue(QuarkusEndpointConfiguration.VAADIN_ENDPOINT_PREFIX, String.class);
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    static boolean writeConnectClient(String prefix, Path customClient) {
        InputStream template = Endpoint.class.getResourceAsStream("/connect-client.default.template.ts");
        if (template != null) {
            try (InputStream is = template;
                    Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
                if (scanner.hasNext()) {
                    String out = scanner.next().replace("{{PREFIX}}", prefix);

                    Files.writeString(customClient, out, StandardCharsets.UTF_8);
                    return true;
                } else {
                    LOGGER.debug("Template file connect-client.default.template.ts is empty.");
                }
            } catch (IOException ex) {
                LOGGER.debug("Cannot read template file connect-client.default.template.ts.", ex);
            }
        } else {
            LOGGER.debug("Cannot find template file connect-client.default.template.ts.");
        }
        return false;
    }
}
