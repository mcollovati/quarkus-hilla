package com.example.application.endpoints.helloworld;

import java.time.Duration;
import java.util.Date;

import com.example.application.entities.UserPOJO;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import dev.hilla.Nonnull;
import reactor.core.publisher.Flux;

import com.vaadin.flow.server.auth.AnonymousAllowed;

@Endpoint
@AnonymousAllowed
public class HelloWorldEndpoint {

    @Nonnull
    public String sayHello(@Nonnull String name) {
        if (name.isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + name;
        }
    }

    @Nonnull
    public String sayComplexHello(@Nonnull UserPOJO user) {
        System.out.println("User: " + user.name() + " " + user.surname());
        if (user.surname().isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + user.name() + " " + user.surname();
        }
    }

    @AnonymousAllowed
    public Flux<@Nonnull String> getClock() {
        return Flux.interval(Duration.ofSeconds(5))
                .onBackpressureDrop()
                .map(_interval -> {
                    System.out.println("====================== SENT");
                    return new Date().toString();
                }).onErrorReturn("OOOOOOOOOOOOOOOOPS");
    }

    @AnonymousAllowed
    public EndpointSubscription<@Nonnull String> getClockCancellable() {
        return EndpointSubscription.of(getClock(), () -> {
            System.getLogger("TESTME").log(System.Logger.Level.INFO, "Subscription has been cancelled");
        });
    }

}
