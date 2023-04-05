package com.example.application;

import dev.hilla.Nonnull;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class ClockService {

    private final String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    public Flux<@Nonnull String> getClock() {
        System.out.println("============== HelloWorldEndpoint getClock " + id);
        return Flux.interval(Duration.ofSeconds(1))
                .onBackpressureDrop()
                .map(_interval -> {
                    System.out.println("====================== SENT " + id);
                    return new Date().toString() + " XXXXX " + id;
                }).onErrorReturn("OOOOOOOOOOOOOOOOPS");
    }
}
