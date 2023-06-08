/*
 * Copyright 2023 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mcollovati.quarkus.hilla.deployment.endpoints;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.hilla.Endpoint;
import dev.hilla.EndpointSubscription;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

@Endpoint
@AnonymousAllowed
public class ReactiveEndpoint {

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public Flux<Integer> count(String counterName) {
        return Flux.interval(Duration.ofMillis(200))
                .map(_interval -> counters.computeIfAbsent(counterName, unused -> new AtomicInteger())
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
