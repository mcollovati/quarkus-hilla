package com.example.application.endpoints.helloworld;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import com.example.application.entities.UserPOJO;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import dev.hilla.Nonnull;
import reactor.core.publisher.Flux;

import com.vaadin.flow.server.auth.AnonymousAllowed;

@Endpoint
@AnonymousAllowed
public class HelloWorldEndpoint {

    private final String id = UUID.randomUUID().toString();

    public HelloWorldEndpoint() {
        System.out.println("============== HelloWorldEndpoint CTOR " + id);
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
        System.out.println("User: " + user.name() + " " + user.surname());
        if (user.surname().isEmpty()) {
            return "Hello stranger";
        } else {
            return "Hello " + user.name() + " " + user.surname();
        }
    }

    @AnonymousAllowed
    public Flux<@Nonnull String> getClock() {
        System.out.println("============== HelloWorldEndpoint getClock " + id);
        return Flux.interval(Duration.ofSeconds(1))
                .onBackpressureDrop()
                .map(_interval -> {
                    System.out.println("====================== SENT " + id);
                    return new Date().toString() + " XXXXX " + id;
                }).onErrorReturn("OOOOOOOOOOOOOOOOPS");
    }

    @AnonymousAllowed
    public EndpointSubscription<@Nonnull String> getClockCancellable() {
        return EndpointSubscription.of(getClock(), () -> {
            System.getLogger("TESTME").log(System.Logger.Level.INFO, "Subscription has been cancelled");
        });
    }

}
