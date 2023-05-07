package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import dev.hilla.Endpoint;

@Endpoint
public class SecureEndpoint {

    @RolesAllowed("ADMIN")
    public String adminOnly() {
        return "ADMIN";
    }

    @RolesAllowed("USER")
    public String userOnly() {
        return "USER";
    }

    @RolesAllowed({ "USER", "ADMIN" })
    public String userAndAdmin() {
        return "USER AND ADMIN";
    }

    @PermitAll
    public String authenticated() {
        return "AUTHENTICATED";
    }

    public String denyByDefault() {
        throw new IllegalArgumentException(
                "Method should be denied by default");
    }

    @DenyAll
    public String deny() {
        throw new IllegalArgumentException("Method denied");
    }

}
