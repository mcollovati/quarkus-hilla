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
import jakarta.annotation.security.RolesAllowed;

import com.example.application.ClockService;
import com.example.application.entities.UserPOJO;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import dev.hilla.Nonnull;
import reactor.core.publisher.Flux;

@Endpoint
@AnonymousAllowed
public class HelloWorldEndpoint {

    private final ClockService clockService;

    public HelloWorldEndpoint(ClockService clockService) {
        this.clockService = clockService;
    }

    @Nonnull
    public String sayHello(@Nonnull String name) {
        if (name.isEmpty()) {
            return "Hello stranger!!!";
        } else {
            return "Hello " + name + "!!!";
        }
    }

    @Nonnull
    public String sayHello2(@Nonnull String name) {
        if (name.isEmpty()) {
            return "Hello from new methods stranger!";
        } else {
            return "Hello from new methods " + name + "!";
        }
    }

    @Nonnull
    public String sayComplexHello(@Nonnull UserPOJO user) {
        if (user.surname().isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + user.name() + " " + user.surname();
        }
    }

    @Nonnull
    @PermitAll
    public String sayHelloProtected() {
        var principal = VaadinRequest.getCurrent().getUserPrincipal();
        if (principal == null) return "Hello anonymous!";
        return "Hello " + principal.getName() + "!!!";
    }

    @PermitAll
    public Flux<@Nonnull String> getClock() {
        return clockService.getClock();
    }

    @RolesAllowed("ADMIN")
    public EndpointSubscription<@Nonnull String> getClockCancellable() {
        return EndpointSubscription.of(getClock(), () -> System.getLogger("TESTME")
                .log(System.Logger.Level.INFO, "Subscription has been cancelled"));
    }

    @AnonymousAllowed
    public Flux<@Nonnull String> getPublicClock(Integer limit) {
        Flux<String> flux = clockService.getClock();
        if (limit != null) {
            flux = flux.take(limit, true);
        }
        return flux.map(msg -> "PUBLIC: " + msg);
    }
}
