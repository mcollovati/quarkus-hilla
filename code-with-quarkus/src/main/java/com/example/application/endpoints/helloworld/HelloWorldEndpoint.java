package com.example.application.endpoints.helloworld;

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

    public static class UserPOJO {

        private String name;
        private String surname;

        public UserPOJO() {
        }

        public UserPOJO(String name, String surname) {
            this.name = name;
            this.surname = surname;
        }

        public String name() {
            return name;
        }

        public String surname() {
            return surname;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSurname(String surname) {
            this.surname = surname;
        }
    }


}
