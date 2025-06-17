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
import com.vaadin.hilla.EndpointSubscription;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.context.ThreadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@ApplicationScoped
@BrowserCallable
@AnonymousAllowed
public class ClockService {

    private final SecurityIdentity securityIdentity;

    public ClockService(SecurityIdentity securityIdentity, ThreadContext threadContext) {
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
    public Flux<@NonNull String> getClock() {
        String userName = getUsername();
        return Flux.interval(Duration.ofSeconds(1))
                .onBackpressureDrop()
                .map(unused -> userName + " " + new Date() + " " + id)
                .doOnError(Throwable::printStackTrace)
                .onErrorReturn("Sorry, something failed...");
    }

    @RolesAllowed("ADMIN")
    public EndpointSubscription<@NonNull String> getClockCancellable() {
        return EndpointSubscription.of(getClock(), () -> System.getLogger("TESTME")
                .log(System.Logger.Level.INFO, "Subscription has been cancelled"));
    }

    @AnonymousAllowed
    public Flux<@NonNull String> getPublicClock(@Nullable Integer limit) {
        Flux<String> flux = getClock();
        if (limit != null) {
            flux = flux.take(limit, true);
        }
        return flux.map(msg -> "PUBLIC: " + msg);
    }
}
