package com.example.application.entities;

import dev.hilla.Nonnull;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.HashSet;
import java.util.Set;

public class UserInfo {

    @Nonnull
    private final String name;

    @Nonnull
    private final Set<String> roles;

    public UserInfo(SecurityIdentity identity) {
        this.name = identity.getPrincipal().getName();
        this.roles = new HashSet<>(identity.getRoles());
    }

    public String getName() {
        return name;
    }

    public Set<String> getRoles() {
        return roles;
    }
}
