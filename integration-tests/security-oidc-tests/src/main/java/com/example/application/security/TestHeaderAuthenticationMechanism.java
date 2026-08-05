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
package com.example.application.security;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

@ApplicationScoped
public class TestHeaderAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String HEADER = "X-Test-User";
    public static final String SCHEME = "test-header";
    public static final String TRANSPORT_ONLY_ADMIN = "transport-only-admin";

    private static final String BASE_IDENTITY_ATTRIBUTE =
            "com.github.mcollovati.quarkus.hilla.security.QuarkusSecurityIdentityAugmentor.base";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String username = context.request().getHeader(HEADER);
        if (username == null || username.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        if (TRANSPORT_ONLY_ADMIN.equals(username) && !context.normalizedPath().startsWith("/connect")) {
            return Uni.createFrom().nullItem();
        }
        QuarkusSecurityIdentity.Builder identityBuilder = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> username)
                .addRole("USER")
                .setAnonymous(false);
        if (TRANSPORT_ONLY_ADMIN.equals(username)) {
            identityBuilder.addRole("ADMIN");
        }
        SecurityIdentity baseIdentity = identityBuilder.build();
        return Uni.createFrom()
                .item(QuarkusSecurityIdentity.builder(baseIdentity)
                        .addAttribute(BASE_IDENTITY_ATTRIBUTE, baseIdentity)
                        .build());
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401));
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom()
                .item(new HttpCredentialTransport(HttpCredentialTransport.Type.OTHER_HEADER, HEADER, SCHEME));
    }
}
