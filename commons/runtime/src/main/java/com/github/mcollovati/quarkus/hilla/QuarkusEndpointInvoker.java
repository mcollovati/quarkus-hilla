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

import jakarta.servlet.ServletContext;
import java.security.Principal;
import java.util.function.Function;

import com.vaadin.hilla.EndpointInvocationException;
import com.vaadin.hilla.EndpointInvoker;
import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.EndpointSubscription;
import com.vaadin.hilla.ExplicitNullableTypeChecker;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Extension of EndpointInvoker that handles transformations for Quarkus types.
 * <p></p>
 * Implemented transformations:
 * - Multi -> Flux
 */
public class QuarkusEndpointInvoker extends EndpointInvoker {

    private final Scheduler scheduler;

    /**
     * Creates an instance of this bean.
     *
     * @param applicationContext          The Spring application context
     * @param endpointObjectMapper        mapper used for serializing and deserializing request and response bodies.
     * @param explicitNullableTypeChecker the method parameter and return value type checker to verify
     *                                    that null values are explicit
     * @param servletContext              the servlet context
     * @param endpointRegistry            the registry used to store endpoint information
     */
    public QuarkusEndpointInvoker(
            ApplicationContext applicationContext,
            ObjectMapper endpointObjectMapper,
            ExplicitNullableTypeChecker explicitNullableTypeChecker,
            ServletContext servletContext,
            EndpointRegistry endpointRegistry,
            ManagedExecutor executor) {
        super(applicationContext, endpointObjectMapper, explicitNullableTypeChecker, servletContext, endpointRegistry);
        scheduler = Schedulers.fromExecutor(executor);
    }

    @Override
    public Class<?> getReturnType(String endpointName, String methodName) {
        Class<?> returnType = super.getReturnType(endpointName, methodName);

        if (returnType != null
                && (Multi.class.isAssignableFrom(returnType)
                        || MutinyEndpointSubscription.class.isAssignableFrom(returnType))) {
            return EndpointSubscription.class;
        }
        return returnType;
    }

    @Override
    public Object invoke(
            String endpointName,
            String methodName,
            ObjectNode body,
            Principal principal,
            Function<String, Boolean> rolesChecker)
            throws EndpointInvocationException.EndpointHttpException {
        Object object = super.invoke(endpointName, methodName, body, principal, rolesChecker);
        if (object instanceof Multi<?> multi) {
            object = multiToEndpointSubscription(multi, null);
        } else if (object instanceof MutinyEndpointSubscription<?> endpointSubscription) {
            object = multiToEndpointSubscription(
                    endpointSubscription.getMulti(), endpointSubscription.getOnUnsubscribe());
        }
        return object;
    }

    @SuppressWarnings({"MutinyCallingSubscribeInNonBlockingScope", "ReactiveStreamsPublisherImplementation"})
    private EndpointSubscription<?> multiToEndpointSubscription(Multi<?> multi, Runnable onUnsubscribe) {
        OnDisconnect onDisconnect = new OnDisconnect(onUnsubscribe);
        Flux<?> flux = Flux.from(subscribe -> {
                    Cancellable cancelable =
                            multi.subscribe().with(subscribe::onNext, subscribe::onError, subscribe::onComplete);
                    onDisconnect.setCancellable(cancelable);
                })
                .cancelOn(scheduler)
                .subscribeOn(scheduler)
                .publishOn(scheduler);
        return EndpointSubscription.of(flux, onDisconnect);
    }

    private static class OnDisconnect implements Runnable {
        private final Runnable onUnsubscribe;
        private Cancellable cancellable;

        OnDisconnect(Runnable onUnsubscribe) {
            this.onUnsubscribe = onUnsubscribe;
        }

        void setCancellable(Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        @Override
        public void run() {
            if (cancellable != null) {
                cancellable.cancel();
            }
            if (onUnsubscribe != null) {
                onUnsubscribe.run();
            }
        }
    }
}
