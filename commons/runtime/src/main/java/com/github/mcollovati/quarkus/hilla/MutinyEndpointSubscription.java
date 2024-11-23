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

import io.smallrye.mutiny.Multi;

/**
 * A subscription that wraps a Multi and allows to listen for unsubscribe events
 * from the browser.
 * <p>
 * An unsubscribe event is sent when "cancel" is called in the browser but also
 * if the browser has disconnected from the server either explicitly or been
 * disconnected from the server for a long enough time.
 * <p>
 * Attribution:
 * This file is based on work from Vaadin Ltd.
 * Copyright 2000-2024 Vaadin Ltd. https://vaadin.com
 * Original source: https://github.com/vaadin/hilla/blob/main/packages/java/endpoint/src/main/java/com/vaadin/hilla/EndpointSubscription.java
 * Changes made:
 * - Replaced reactor Flux type with Mutiny Multi.
 */
public class MutinyEndpointSubscription<TT> {

    private Multi<TT> multi;
    private Runnable onUnsubscribe;

    private MutinyEndpointSubscription(Multi<TT> multi, Runnable onUnsubscribe) {
        this.multi = multi;
        this.onUnsubscribe = onUnsubscribe;
    }

    /**
     * Returns the multi value provide for this subscription.
     */
    public Multi<TT> getMulti() {
        return multi;
    }

    /**
     * Returns the callback that is invoked when the browser unsubscribes from
     * the subscription.
     */
    public Runnable getOnUnsubscribe() {
        return onUnsubscribe;
    }

    /**
     * Creates a new endpoint subscription.
     *
     * A subscription wraps a multi that provides the values for the subscriber
     * (browser) and a callback that is invoked when the browser unsubscribes
     * from the subscription.
     *
     * @param <T>
     *            the type of data in the subscription
     * @param flux
     *            the multi that produces the data
     * @param onDisconnect
     *            a callback that is invoked when the browser unsubscribes
     * @return a subscription
     */
    public static <T> MutinyEndpointSubscription<T> of(Multi<T> flux, Runnable onDisconnect) {
        return new MutinyEndpointSubscription<>(flux, onDisconnect);
    }
}
