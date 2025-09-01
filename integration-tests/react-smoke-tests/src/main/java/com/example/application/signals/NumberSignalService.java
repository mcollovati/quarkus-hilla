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
package com.example.application.signals;

import jakarta.validation.constraints.NotNull;
import java.util.Optional;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.signals.NumberSignal;

@AnonymousAllowed
@BrowserCallable
public class NumberSignalService {
    public static final double INITIAL_SHARED_VALUE = 0.5;
    private final NumberSignal counter = new NumberSignal();
    private final NumberSignal sharedValue = new NumberSignal(INITIAL_SHARED_VALUE);

    public NumberSignal counter() {
        return counter;
    }

    public NumberSignal sharedValue() {
        return sharedValue;
    }

    @NotNull public Double fetchSharedValue() {
        return sharedValue.peek();
    }

    @NotNull public Long fetchCounterValue() {
        return Optional.ofNullable(counter.peek()).map(Double::longValue).orElse(null);
    }
}
