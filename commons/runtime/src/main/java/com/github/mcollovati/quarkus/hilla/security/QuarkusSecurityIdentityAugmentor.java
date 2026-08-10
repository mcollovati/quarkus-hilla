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

import jakarta.inject.Singleton;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.smallrye.mutiny.Uni;

/**
 * Captures the authenticated identity after application augmentors but before
 * HTTP path policies can augment it for the transport request.
 */
@Singleton
public class QuarkusSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    static final String BASE_IDENTITY_ATTRIBUTE = QuarkusSecurityIdentityAugmentor.class.getName() + ".base";

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity == null
                || identity.isAnonymous()
                || identity.getAttribute(BASE_IDENTITY_ATTRIBUTE) instanceof SecurityIdentity) {
            return Uni.createFrom().item(identity);
        }
        return Uni.createFrom()
                .item(new SecurityIdentityWithAttributes(
                        identity, java.util.Map.of(BASE_IDENTITY_ATTRIBUTE, identity)));
    }

    static SecurityIdentity baseIdentity(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return identity;
        }
        Object baseIdentity = identity.getAttribute(BASE_IDENTITY_ATTRIBUTE);
        return baseIdentity instanceof SecurityIdentity ? (SecurityIdentity) baseIdentity : null;
    }
}
