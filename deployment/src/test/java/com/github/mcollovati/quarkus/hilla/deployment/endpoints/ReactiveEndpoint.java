package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import reactor.core.publisher.Flux;

import com.vaadin.flow.server.auth.AnonymousAllowed;

@Endpoint
@AnonymousAllowed
public class ReactiveEndpoint {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public Flux<Integer> count(String counterName) {
        return Flux.interval(Duration.ofMillis(200)).onBackpressureDrop()
                .map(_interval -> counters
                        .computeIfAbsent(counterName,
                                unused -> new AtomicInteger())
                        .incrementAndGet());
    }

    public EndpointSubscription<Integer> cancelableCount(String counterName) {
        return EndpointSubscription.of(count(counterName), () -> {
            counters.get(counterName).set(-1);
        });
    }

    public Integer counterValue(String counterName) {
        if (counters.containsKey(counterName)) {
            return counters.get(counterName).get();
        }
        return null;
    }
}
