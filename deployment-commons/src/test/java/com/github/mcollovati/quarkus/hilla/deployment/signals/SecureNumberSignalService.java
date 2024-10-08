/*
 * Copyright 2024 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.deployment.signals;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import java.util.UUID;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.signals.NumberSignal;
import com.vaadin.hilla.signals.core.event.StateEvent;

@BrowserCallable
public class SecureNumberSignalService {

    private final NumberSignal userCounter = new NumberSignal(20d);
    private final NumberSignal adminCounter = new NumberSignal(30d);

    @PermitAll
    public NumberSignal userCounter() {
        return userCounter;
    }

    @RolesAllowed("ADMIN")
    public NumberSignal adminCounter() {
        return adminCounter;
    }

    @AnonymousAllowed
    public void resetCounters() {
        var setValueTo20 = new StateEvent<>(UUID.randomUUID().toString(), StateEvent.EventType.SET, 20d);
        userCounter.submit(setValueTo20.toJson());

        var setValueTo30 = new StateEvent<>(UUID.randomUUID().toString(), StateEvent.EventType.SET, 30d);
        adminCounter.submit(setValueTo30.toJson());
    }
}
