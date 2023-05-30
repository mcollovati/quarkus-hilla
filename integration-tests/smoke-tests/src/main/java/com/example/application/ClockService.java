package com.example.application;

import dev.hilla.Nonnull;
import io.quarkus.security.identity.SecurityIdentity;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.context.ThreadContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@ApplicationScoped
public class ClockService {

    private final SecurityIdentity securityIdentity;

    public ClockService(SecurityIdentity securityIdentity, ThreadContext threadContext) {
        this.securityIdentity = securityIdentity;
        Schedulers.onScheduleHook("managed-thread", threadContext::contextualRunnable);
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
