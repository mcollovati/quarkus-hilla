package org.acme.hilla.test.extension;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.RoutingContext;

public class HillaFormAuthenticationMechanism
        implements HttpAuthenticationMechanism {
    private String logoutPath;
    private String cookieName;

    FormAuthenticationMechanism delegate;

    public HillaFormAuthenticationMechanism(
            FormAuthenticationMechanism delegate, String cookieName,
            String logoutPath) {
        this.delegate = delegate;
        this.logoutPath = logoutPath;
        this.cookieName = cookieName;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        if (context.normalizedPath().equals(logoutPath)) {
            logout(context);
            return Uni.createFrom().optional(Optional.empty());
        }
        return delegate.authenticate(context, identityProviderManager);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return delegate.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return delegate.getCredentialTypes();
    }

    private void logout(RoutingContext ctx) {
        // Vert.x sends back a set-cookie with max-age and expiry but no path,
        // so we have to set it first,
        // otherwise web clients don't clear it
        Cookie cookie = ctx.request().getCookie(cookieName);
        if (cookie != null) {
            cookie.setPath("/");
        }
        ctx.response().removeCookie(cookieName);
    }

}
