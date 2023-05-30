package com.example.application.endpoints.helloworld;

import com.example.application.ClockService;
import com.example.application.entities.UserPOJO;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import dev.hilla.Nonnull;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import reactor.core.publisher.Flux;

@Endpoint
@AnonymousAllowed
public class HelloWorldEndpoint {

    private final ClockService clockService;

    public HelloWorldEndpoint(ClockService clockService) {
        this.clockService = clockService;
        System.out.println("============== HelloWorldEndpoint CTOR " + clockService.getId());
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

    @Nonnull
    @PermitAll
    public String sayHelloProtected() {
        var principal = VaadinRequest.getCurrent().getUserPrincipal();
        if (principal == null) return "Hello anonymous!";
        return "Hello " + principal.getName() + "!!!";
    }

    @PermitAll
    public Flux<@Nonnull String> getClock() {
        System.out.println("============== HelloWorldEndpoint getClock " + clockService.getId());
        return clockService.getClock();
    }

    @RolesAllowed("ADMIN")
    public EndpointSubscription<@Nonnull String> getClockCancellable() {
        return EndpointSubscription.of(getClock(), () -> System.getLogger("TESTME")
                .log(System.Logger.Level.INFO, "Subscription has been cancelled"));
    }
}
