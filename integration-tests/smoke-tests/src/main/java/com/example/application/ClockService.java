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
package com.example.application;

import dev.hilla.Nonnull;
import io.quarkus.security.identity.SecurityIdentity;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.context.ThreadContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@ApplicationScoped
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

    public Flux<@Nonnull String> getClock() {
        String userName = getUsername();
        return Flux.interval(Duration.ofSeconds(1))
                .onBackpressureDrop()
                .map(unused -> userName + " " + new Date() + " " + id)
                .doOnError(Throwable::printStackTrace)
                .onErrorReturn("Sorry, something failed...");
    }

    private String getUsername() {
        if (securityIdentity.isAnonymous()) {
            return "Anonymous";
        } else {
            return securityIdentity.getPrincipal().getName();
        }
    }
}
