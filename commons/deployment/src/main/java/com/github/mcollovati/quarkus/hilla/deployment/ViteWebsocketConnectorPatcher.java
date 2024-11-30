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
package com.github.mcollovati.quarkus.hilla.deployment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;

import com.github.mcollovati.quarkus.hilla.ViteTemp;

public class ViteWebsocketConnectorPatcher {

    public void apply(BuildProducer<BytecodeTransformerBuildItem> producer) {
        producer.produce(new BytecodeTransformerBuildItem(
                "com.vaadin.base.devserver.viteproxy.ViteWebsocketConnection", patchViteWebsocketConnection()));
    }

    private BiFunction<String, ClassVisitor, ClassVisitor> patchViteWebsocketConnection() {
        return (className, classVisitor) -> {
            ClassTransformer transformer = new ClassTransformer(className);
            // (ILjava/lang/String;Ljava/lang/String;Ljava/util/function/Consumer;Ljava/lang/Runnable;Ljava/util/function/Consumer;)V
            MethodDescriptor ctorDescr = MethodDescriptor.ofMethod(
                    className,
                    "<init>",
                    "V",
                    "I",
                    "java.lang.String",
                    "java.lang.String",
                    "java.util.function.Consumer",
                    "java.lang.Runnable",
                    "java.util.function.Consumer");
            transformer.removeMethod(ctorDescr);
            // transformer.modifyMethod(ctorDescr).rename("myctor");

            /*
            try (MethodCreator creator = transformer.addMethod(MethodDescriptor.ofMethod(
                    className, "doWhenComplete", void.class, WebSocket.class, Throwable.class
            ))) {
                var ifFailureNull = creator.ifNull(creator.getMethodParam(1));

                // If failure == null
                try (var trueCreator = ifFailureNull.trueBranch()) {
                    trueCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(CompletableFuture.class, "complete", void.class, Object.class),
                            trueCreator.readInstanceField(FieldDescriptor.of(className, "clientWebsocket", CompletableFuture.class), creator.getThis()),
                            trueCreator.getMethodParam(0)
                    );
                    trueCreator.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(Logger.class, "debug", void.class, String.class, Object[].class),
                            trueCreator.invokeStaticMethod(MethodDescriptor.ofMethod(className, "getLogger", Logger.class)),
                            trueCreator.load("Connection to ##URI## using the ##proto## protocol established")
                    );

                    // Else branch: handle failure
                    try (var elseBranch = ifFailureNull.falseBranch()) {
                        elseBranch.invokeInterfaceMethod(
                                MethodDescriptor.ofMethod(CompletableFuture.class, "completeExceptionally", void.class, Throwable.class),
                                elseBranch.readInstanceField(FieldDescriptor.of(className, "clientWebsocket", CompletableFuture.class), creator.getThis()),
                                creator.getMethodParam(1)
                        );

                        elseBranch.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(Logger.class, "debug", void.class, String.class, Object[].class),
                                trueCreator.invokeStaticMethod(MethodDescriptor.ofMethod(className, "getLogger", Logger.class)),
                                creator.load("Failed to connect to ##uri##")
                        );
                        /*
                        elseBranch.invokeInterfaceMethod(
                                MethodDescriptor.ofMethod(Consumer.class, "accept", void.class, Object.class),
                                creator.getMethodParam(5),
                                failureParam
                        );
                         * /
                    }
                    creator.returnNull();
                }
            }
             */

            try (MethodCreator creator = transformer.addMethod(ctorDescr)) {
                creator.setModifiers(Opcodes.ACC_PUBLIC);
                creator.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), creator.getThis());
                creator.writeInstanceField(
                        FieldDescriptor.of(className, "parts", "java.util.List"),
                        creator.getThis(),
                        creator.newInstance(MethodDescriptor.ofConstructor(ArrayList.class)));
                creator.writeInstanceField(
                        FieldDescriptor.of(className, "onMessage", "java.util.function.Consumer"),
                        creator.getThis(),
                        creator.getMethodParam(3));
                creator.writeInstanceField(
                        FieldDescriptor.of(className, "onClose", "java.lang.Runnable"),
                        creator.getThis(),
                        creator.getMethodParam(4));
                /*
                AssignableResultHandle wsHost = creator.createVariable("java.lang.String");
                creator.assign(wsHost,
                        creator.invokeVirtualMethod(MethodDescriptor.ofMethod(
                                        String.class, "replace", String.class, CharSequence.class, CharSequence.class
                                ), creator.readStaticField(FieldDescriptor.of(
                                        "com.vaadin.base.devserver.ViteHandler", "DEV_SERVER_HOST", "java.lang.String"
                                )), creator.load("http://"), creator.load("ws://")
                        ));
                 */
                var wsHost = creator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(
                                String.class, "replace", String.class, CharSequence.class, CharSequence.class),
                        creator.readStaticField(FieldDescriptor.of(
                                "com.vaadin.base.devserver.ViteHandler", "DEV_SERVER_HOST", String.class)),
                        creator.load("http://"),
                        creator.load("ws://"));
                // AssignableResultHandle uri = creator.createVariable("java.net.URI");
                MethodDescriptor concatMethod =
                        MethodDescriptor.ofMethod(String.class, "concat", String.class, String.class);
                var uri = creator.invokeStaticMethod(
                        MethodDescriptor.ofMethod(URI.class, "create", URI.class, String.class),
                        creator.invokeVirtualMethod(
                                concatMethod,
                                creator.invokeVirtualMethod(
                                        concatMethod,
                                        wsHost,
                                        creator.invokeVirtualMethod(
                                                concatMethod,
                                                creator.load(":"),
                                                creator.invokeVirtualMethod(
                                                        MethodDescriptor.ofMethod(
                                                                Integer.class, "toString", String.class),
                                                        creator.getMethodParam(0)))),
                                creator.getMethodParam(1)));

                // Initialize CompletableFuture
                ResultHandle websocketFuture =
                        creator.newInstance(MethodDescriptor.ofConstructor(CompletableFuture.class));
                creator.writeInstanceField(
                        FieldDescriptor.of(className, "clientWebsocket", CompletableFuture.class),
                        creator.getThis(),
                        websocketFuture);

                // Set up HttpClient and WebSocket builder
                var httpClient = creator.invokeStaticMethod(
                        MethodDescriptor.ofMethod(HttpClient.class, "newHttpClient", HttpClient.class));

                var webSocketBuilder = creator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(HttpClient.class, "newWebSocketBuilder", WebSocket.Builder.class),
                        httpClient);

                var webSocket = creator.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(
                                WebSocket.Builder.class,
                                "subprotocols",
                                WebSocket.Builder.class,
                                String.class,
                                String[].class),
                        webSocketBuilder,
                        creator.getMethodParam(2),
                        creator.newArray(String.class, 0));

                var buildAsyncFuture = creator.invokeInterfaceMethod(
                        MethodDescriptor.ofMethod(
                                WebSocket.Builder.class,
                                "buildAsync",
                                CompletableFuture.class,
                                URI.class,
                                WebSocket.Listener.class),
                        webSocket,
                        uri,
                        creator.getThis());

                creator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(
                                CompletableFuture.class, "whenComplete", CompletableFuture.class, BiConsumer.class),
                        buildAsyncFuture,
                        creator.newInstance(
                                MethodDescriptor.ofConstructor(
                                        ViteTemp.class, CompletableFuture.class, URI.class, Consumer.class),
                                websocketFuture,
                                uri,
                                creator.getMethodParam(5)));
                creator.returnNull();
            }

            MethodDescriptor onOpen = MethodDescriptor.ofMethod(className, "onOpen", void.class, WebSocket.class);
            transformer.modifyMethod(onOpen).rename("doOnOpen");
            try (var creator = transformer.addMethod(onOpen)) {
                creator.setModifiers(Opcodes.ACC_PUBLIC);
                ResultHandle isSet = creator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(CompletableFuture.class, "complete", boolean.class, Object.class),
                        creator.readInstanceField(
                                FieldDescriptor.of(className, "clientWebsocket", CompletableFuture.class),
                                creator.getThis()),
                        creator.getMethodParam(0));
                try (var trueCreator = creator.ifTrue(isSet).trueBranch()) {
                    trueCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(Logger.class, "debug", void.class, String.class),
                            trueCreator.invokeStaticMethod(
                                    MethodDescriptor.ofMethod(className, "getLogger", Logger.class)),
                            creator.load("Websocket set by onOpen"));
                }
                creator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(className, "doOnOpen", void.class, WebSocket.class),
                        creator.getThis(),
                        creator.getMethodParam(0));
                creator.returnNull();
            }
            return transformer.applyTo(classVisitor);
        };
    }
}
