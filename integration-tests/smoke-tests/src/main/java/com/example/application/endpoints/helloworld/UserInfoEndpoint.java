package com.example.application.endpoints.helloworld;

import com.example.application.entities.UserInfo;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.Nonnull;
import io.quarkus.security.identity.SecurityIdentity;
import javax.annotation.security.PermitAll;

@Endpoint
@PermitAll
public class UserInfoEndpoint {

    SecurityIdentity securityIdentity;

    public UserInfoEndpoint(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    @AnonymousAllowed
    @Nonnull
    public UserInfo me() {
        if (securityIdentity.isAnonymous()) {
            return null;
        }
        return new UserInfo(securityIdentity);
    }
}
