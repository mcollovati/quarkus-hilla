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
package com.example.application.services;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.util.Set;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;

@Path("/security/probe")
@Blocking
public class SecurityProbeResource {

    private final SecurityIdentity securityIdentity;

    public SecurityProbeResource(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    @POST
    @Path("roles")
    public Set<String> roles() {
        return securityIdentity.getRoles();
    }
}
