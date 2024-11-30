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

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vaadin.base.devserver.viteproxy.ViteWebsocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViteTemp implements BiConsumer<WebSocket, Throwable> {

    private static Logger getLogger() {
        return LoggerFactory.getLogger(ViteWebsocketConnection.class);
    }

    private final URI uri;
    private final Consumer<Throwable> onConnectionFailure;
    private final CompletableFuture<WebSocket> future;

    public ViteTemp(CompletableFuture<WebSocket> future, URI uri, Consumer<Throwable> errorHandler) {
        this.uri = uri;
        this.onConnectionFailure = errorHandler;
        this.future = future;
    }

    @Override
    public void accept(WebSocket webSocket, Throwable failure) {
        if (failure == null) {
            getLogger().debug("Connection to {} using the {} protocol established", uri, webSocket.getSubprotocol());
            if (future.complete(webSocket)) {
                getLogger().debug("Websocket set by whenComplete");
            }
        } else {
            getLogger().debug("Failed to connect to {}", uri);
            onConnectionFailure.accept(failure);
        }
    }
}
