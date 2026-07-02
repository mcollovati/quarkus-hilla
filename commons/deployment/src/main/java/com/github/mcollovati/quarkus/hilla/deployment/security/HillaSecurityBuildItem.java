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

import io.quarkus.builder.item.SimpleBuildItem;

public final class HillaSecurityBuildItem extends SimpleBuildItem {

    private final SecurityModel securityModel;

    public HillaSecurityBuildItem(SecurityModel securityModel) {
        this.securityModel = securityModel;
    }

    public SecurityModel getSecurityModel() {
        return securityModel;
    }

    boolean isAuthEnabled() {
        return securityModel != SecurityModel.NONE;
    }

    boolean isFormAuthEnabled() {
        return securityModel == SecurityModel.FORM;
    }

    enum SecurityModel {
        NONE,
        FORM,
        OIDC,
        OAUTH2,
        BASIC,
        JWT,
        JPA,
        JDBC,
        LDAP,
        SECURITY_EXTENSION
    }
}
