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
package com.example.application.entities;

import java.util.HashSet;
import java.util.Set;

import io.quarkus.security.identity.SecurityIdentity;
import org.jspecify.annotations.NonNull;

public class UserInfo {

    @NonNull private final String name;

    @NonNull private final Set<String> roles;

    public UserInfo(SecurityIdentity identity) {
        this.name = identity.getPrincipal().getName();
        this.roles = new HashSet<>(identity.getRoles());
    }

    public String getName() {
        return name;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
