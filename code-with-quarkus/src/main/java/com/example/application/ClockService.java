package com.example.application;

import javax.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import dev.hilla.Nonnull;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@ApplicationScoped
public class ClockService {

    private final SecurityIdentity securityIdentity;

    public ClockService(SecurityIdentity securityIdentity) {
        this.securityIdentity = securityIdentity;
    }

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    public Flux<@Nonnull String> getClock() {
        String userName = getUsername();
        return Flux.interval(Duration.ofSeconds(1))
                .onBackpressureDrop()
                .map(_interval -> {
                    System.out.println("====================== SENT " + id);
                    return userName + " " + new Date().toString() + " " + id;
                })
                .doOnError(Throwable::printStackTrace)
                .onErrorReturn("Sorry, something failed...");
    }

    private String getUsername() {
        if (securityIdentity.isAnonymous()) {
            return "Anonymous";
        } else {
            return securityIdentity.getPrincipal().getName();
        }
    }
}
