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
package com.example.application.endpoints.helloworld;

import jakarta.annotation.security.PermitAll;

import com.example.application.entities.UserInfo;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.Endpoint;
import io.quarkus.security.identity.SecurityIdentity;

@Endpoint
@PermitAll
public class UserInfoEndpoint {

    SecurityIdentity securityIdentity;

    public UserInfoEndpoint(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    @AnonymousAllowed
    public UserInfo me() {
        if (securityIdentity.isAnonymous()) {
            return null;
        }
        return new UserInfo(securityIdentity);
    }
}
