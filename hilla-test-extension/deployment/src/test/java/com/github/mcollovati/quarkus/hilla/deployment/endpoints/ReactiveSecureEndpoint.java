package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import dev.hilla.Endpoint;
import reactor.core.publisher.Flux;

@Endpoint
public class ReactiveSecureEndpoint {

    @RolesAllowed("ADMIN")
    public Flux<String> adminOnly() {
        return Flux.just("ADMIN");
    }

    @RolesAllowed("USER")
    public Flux<String> userOnly() {
        return Flux.just("USER");
    }

    @RolesAllowed({ "USER", "ADMIN" })
    public Flux<String> userAndAdmin() {
        return Flux.just("USER AND ADMIN");
    }

    @PermitAll
    public Flux<String> authenticated() {
        return Flux.just("AUTHENTICATED");
    }

    public Flux<String> denyByDefault() {
        throw new IllegalArgumentException(
                "Method should be denied by default");
    }

    @DenyAll
    public Flux<String> deny() {
        throw new IllegalArgumentException("Method denied");
    }

}
