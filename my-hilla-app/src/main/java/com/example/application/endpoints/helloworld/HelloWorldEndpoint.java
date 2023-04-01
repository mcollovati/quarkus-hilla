package com.example.application.endpoints.helloworld;

import java.time.Duration;
import java.util.Date;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import dev.hilla.Nonnull;
import reactor.core.publisher.Flux;

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