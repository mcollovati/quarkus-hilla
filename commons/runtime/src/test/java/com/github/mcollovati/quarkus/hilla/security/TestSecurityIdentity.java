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

import java.security.Permission;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

final class TestSecurityIdentity implements SecurityIdentity {

    private final Principal principal;
    private final boolean anonymous;
    private final Set<String> roles;

    private TestSecurityIdentity(String principalName, boolean anonymous, Set<String> roles) {
        this.principal = () -> principalName;
        this.anonymous = anonymous;
        this.roles = roles;
    }

    static TestSecurityIdentity authenticated(String principalName, String... roles) {
        return new TestSecurityIdentity(principalName, false, Set.of(roles));
    }

    static TestSecurityIdentity anonymous() {
        return new TestSecurityIdentity("", true, Set.of());
    }

    @Override
    public Principal getPrincipal() {
        return principal;
    }

    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public <T extends Credential> T getCredential(Class<T> credentialType) {
        return null;
    }

    @Override
    public Set<Credential> getCredentials() {
        return Set.of();
    }

    @Override
    public Set<Permission> getPermissions() {
        return Set.of();
    }

    @Override
    public <T> T getAttribute(String name) {
        return null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Uni<Boolean> checkPermission(Permission permission) {
        return Uni.createFrom().item(false);
    }
}
