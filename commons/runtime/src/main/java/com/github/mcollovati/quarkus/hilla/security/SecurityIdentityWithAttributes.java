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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

final class SecurityIdentityWithAttributes implements SecurityIdentity {

    private final SecurityIdentity delegate;
    private final Map<String, Object> attributes;

    SecurityIdentityWithAttributes(SecurityIdentity delegate, Map<String, Object> additionalAttributes) {
        this.delegate = delegate;
        Map<String, Object> attributes = new HashMap<>(delegate.getAttributes());
        attributes.putAll(additionalAttributes);
        this.attributes = Map.copyOf(attributes);
    }

    @Override
    public Principal getPrincipal() {
        return delegate.getPrincipal();
    }

    @Override
    public boolean isAnonymous() {
        return delegate.isAnonymous();
    }

    @Override
    public Set<String> getRoles() {
        return delegate.getRoles();
    }

    @Override
    public boolean hasRole(String role) {
        return delegate.hasRole(role);
    }

    @Override
    public Set<Permission> getPermissions() {
        return delegate.getPermissions();
    }

    @Override
    public <T extends Credential> T getCredential(Class<T> credentialType) {
        return delegate.getCredential(credentialType);
    }

    @Override
    public Set<Credential> getCredentials() {
        return delegate.getCredentials();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Uni<Boolean> checkPermission(Permission permission) {
        return delegate.checkPermission(permission);
    }

    @Override
    public boolean checkPermissionBlocking(Permission permission) {
        return delegate.checkPermissionBlocking(permission);
    }
}
