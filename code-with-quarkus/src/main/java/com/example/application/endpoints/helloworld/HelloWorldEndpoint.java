package com.example.application.endpoints.helloworld;

import com.example.application.entities.UserPOJO;
import dev.hilla.Endpoint;
import dev.hilla.Nonnull;

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
}
