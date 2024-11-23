/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.example.application;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Nonnull;
import com.vaadin.hilla.Nullable;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Multi;
import org.eclipse.microprofile.context.ThreadContext;
import reactor.core.scheduler.Schedulers;

import com.github.mcollovati.quarkus.hilla.MutinyEndpointSubscription;

@ApplicationScoped
@BrowserCallable
@AnonymousAllowed
public class MutinyClockService {

    private final SecurityIdentity securityIdentity;

    public MutinyClockService(SecurityIdentity securityIdentity, ThreadContext threadContext) {
        this.securityIdentity = securityIdentity;
        Schedulers.onScheduleHook("managed-thread", threadContext::contextualRunnable);
    }

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    private String getUsername() {
        if (securityIdentity.isAnonymous()) {
            return "Anonymous";
        } else {
            return securityIdentity.getPrincipal().getName();
        }
    }

    @PermitAll
    public Multi<@Nonnull String> getClock() {
        String userName = getUsername();
        return Multi.createFrom()
                .ticks()
                .startingAfter(Duration.ofSeconds(1))
                .every(Duration.ofSeconds(1))
                .onOverflow()
                .drop()
                .map(unused -> userName + " " + new Date() + " " + id)
                .onFailure()
                .recoverWithItem(err -> {
                    err.printStackTrace();
                    return "Sorry, something failed...";
                });
    }

    @RolesAllowed("ADMIN")
    public MutinyEndpointSubscription<@Nonnull String> getClockCancellable() {
        return MutinyEndpointSubscription.of(getClock(), () -> System.getLogger("TESTME")
                .log(System.Logger.Level.INFO, "Subscription has been cancelled"));
    }

    @AnonymousAllowed
    public Multi<@Nonnull String> getPublicClock(@Nullable Integer limit) {
        Multi<String> flux = getClock();
        if (limit != null) {
            flux = flux.capDemandsTo(limit);
        }
        return flux.map(msg -> "PUBLIC: " + msg);
    }
}
