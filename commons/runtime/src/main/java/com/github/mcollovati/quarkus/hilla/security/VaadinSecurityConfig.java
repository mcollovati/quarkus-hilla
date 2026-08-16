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

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration properties for Vaadin security.
 */
@ConfigMapping(prefix = "vaadin.security")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface VaadinSecurityConfig {

    /**
     * The path of the logout HTTP POST endpoint handling logout requests.
     * <p></p>
     * Defaults to {@literal /logout}.
     *
     * @return the path of the logout endpoint.
     */
    @WithDefault("/logout")
    String logoutPath();

    /**
     * The post-logout redirect uri.
     * <p></p>
     * @return post-logout redirect uri.
     */
    Optional<String> postLogoutRedirectUri();

    /**
     * Whether HTTP session should be invalidated on logout.
     * <p></p>
     * Defaults to {@literal true}.
     *
     * @return whether HTTP session should be invalidated on logout.
     */
    @WithDefault("true")
    boolean logoutInvalidateSession();

    /**
     * Settings for the access check on Flow views.
     *
     * @return the navigation access control settings.
     */
    NavigationAccessControlConfig navigationAccessControl();

    /**
     * Settings for the access check on Flow views.
     */
    interface NavigationAccessControlConfig {

        /**
         * Whether access to Flow views is checked.
         * <p></p>
         * Each view needs an annotation that says who may open it, either on
         * the view or on a layout the view uses: {@code @AnonymousAllowed} for
         * everyone, {@code @PermitAll} for logged-in users,
         * {@code @RolesAllowed} for the given roles, {@code @DenyAll} for
         * nobody. A view with no annotation is not shown, which is the Vaadin
         * default.
         * <p></p>
         * Set to {@literal false} to skip the check for all views. Then anyone
         * who can open the application can open every Flow view, and the
         * annotations do nothing. Use this while adding the missing
         * annotations, not as a permanent setting.
         * <p></p>
         * Applications without authentication are not affected.
         * <p></p>
         * Defaults to {@literal true}.
         *
         * @return whether access to Flow views is checked.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
