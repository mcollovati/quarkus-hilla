/*
 * Copyright 2023 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import dev.hilla.Endpoint;
import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

@Endpoint
public class SecureEndpoint {

    @RolesAllowed("ADMIN")
    public String adminOnly() {
        return "ADMIN";
    }

    @RolesAllowed("USER")
    public String userOnly() {
        return "USER";
    }

    @RolesAllowed({"USER", "ADMIN"})
    public String userAndAdmin() {
        return "USER AND ADMIN";
    }

    @PermitAll
    public String authenticated() {
        return "AUTHENTICATED";
    }

    public String denyByDefault() {
        throw new IllegalArgumentException("Method should be denied by default");
    }

    @DenyAll
    public String deny() {
        throw new IllegalArgumentException("Method denied");
    }
}
