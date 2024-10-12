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
package com.github.mcollovati.quarkus.hilla;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Singleton;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.arc.Unremovable;

/**
 * A {@link VaadinServiceInitListener} implementation that forward the {@link ServiceInitEvent} to the extension CDI
 * configuration to be able to initialize the endpoint registry.
 *
 * Unfortunately, eager loading endpoint bean may fail because of Quarkus SecurityConfig instance is not yet available.
 * The service init event is saved into QuarkusEndpointControllerConfiguration so that the endpoint registry can be
 * initialized in a @Startup observer.
 */
@VaadinServiceEnabled
@Singleton
@Unremovable
public class QuarkusVaadinServiceListenerPropagator implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        CDI.current().select(QuarkusEndpointControllerConfiguration.class).get().onVaadinServiceInit(event);
        CDI.current().select(VaadinServiceInitListener.class).forEach(listener -> listener.serviceInit(event));
    }
}
