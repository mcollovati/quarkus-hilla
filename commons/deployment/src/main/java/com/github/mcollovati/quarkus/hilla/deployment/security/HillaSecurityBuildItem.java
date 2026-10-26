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

import java.util.Objects;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The Quarkus authentication mechanism that activates the Hilla and Flow
 * security integration.
 */
public final class HillaSecurityBuildItem extends SimpleBuildItem {

    private final SecurityModel securityModel;

    /**
     * Creates the build item.
     *
     * @param securityModel the detected security model, not null
     */
    public HillaSecurityBuildItem(SecurityModel securityModel) {
        this.securityModel = Objects.requireNonNull(securityModel, "Security model must not be null");
    }

    /**
     * Gets the detected security model.
     *
     * @return the security model, {@link SecurityModel#NONE} if the integration is not active
     */
    public SecurityModel getSecurityModel() {
        return securityModel;
    }

    /**
     * Checks whether the security integration is active.
     *
     * @return {@code true} if a supported authentication mechanism is configured
     */
    public boolean isEnabled() {
        return securityModel != SecurityModel.NONE;
    }

    /**
     * Checks whether form authentication is used, which also installs the Hilla
     * form login and logout handling.
     *
     * @return {@code true} for form authentication
     */
    public boolean isFormAuthentication() {
        return securityModel == SecurityModel.FORM;
    }

    /**
     * Authentication mechanisms that activate the security integration, in
     * order of precedence when more than one is configured.
     */
    public enum SecurityModel {
        /** Form authentication, {@code quarkus.http.auth.form.enabled=true}. */
        FORM,
        /** OpenID Connect, provided by the {@code quarkus-oidc} extension. */
        OIDC,
        /** Bearer JWT, provided by the {@code quarkus-smallrye-jwt} extension. */
        JWT,
        /** Basic authentication, {@code quarkus.http.auth.basic=true}. */
        BASIC,
        /** No supported mechanism, the integration is not active. */
        NONE
    }
}
