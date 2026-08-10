/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.security;

import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.arc.ClientProxy;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HillaFormAuthenticationMechanismTest {

    private static final String AUTHENTICATION_MECHANISM_KEY = HttpAuthenticationMechanism.class.getName();

    @Test
    void authenticate_delegateProxySelectsContextualInstance_restoresWrapper() {
        FormAuthenticationMechanism contextualDelegate = mock(FormAuthenticationMechanism.class);
        FormAuthenticationMechanism delegate =
                mock(FormAuthenticationMechanism.class, Mockito.withSettings().extraInterfaces(ClientProxy.class));
        when(((ClientProxy) delegate).arc_contextualInstance()).thenReturn(contextualDelegate);
        AtomicReference<Object> selectedMechanism = new AtomicReference<>();
        RoutingContext context = routingContext(selectedMechanism);
        doAnswer(invocation -> {
                    selectedMechanism.set(contextualDelegate);
                    return Uni.createFrom().nullItem();
                })
                .when(delegate)
                .authenticate(eq(context), any());
        HillaFormAuthenticationMechanism wrapper = mechanism(delegate);

        wrapper.authenticate(context, mock(IdentityProviderManager.class));

        assertSame(wrapper, selectedMechanism.get());
    }

    @Test
    void authenticate_foreignMechanismSelected_doesNotOverwriteIt() {
        FormAuthenticationMechanism delegate = mock(FormAuthenticationMechanism.class);
        HttpAuthenticationMechanism foreignMechanism = mock(HttpAuthenticationMechanism.class);
        AtomicReference<Object> selectedMechanism = new AtomicReference<>();
        RoutingContext context = routingContext(selectedMechanism);
        doAnswer(invocation -> {
                    selectedMechanism.set(foreignMechanism);
                    return Uni.createFrom().nullItem();
                })
                .when(delegate)
                .authenticate(eq(context), any());
        HillaFormAuthenticationMechanism wrapper = mechanism(delegate);

        wrapper.authenticate(context, mock(IdentityProviderManager.class));

        assertSame(foreignMechanism, selectedMechanism.get());
    }

    @Test
    void authenticationMetadata_isDelegatedTransparently() {
        FormAuthenticationMechanism delegate = mock(FormAuthenticationMechanism.class);
        RoutingContext context = mock(RoutingContext.class);
        HttpCredentialTransport transport = mock(HttpCredentialTransport.class);
        Uni<HttpCredentialTransport> transportResult = Uni.createFrom().item(transport);
        when(delegate.getCredentialTransport(context)).thenReturn(transportResult);
        when(delegate.getPriority()).thenReturn(123);
        HillaFormAuthenticationMechanism wrapper = mechanism(delegate);

        assertSame(transportResult, wrapper.getCredentialTransport(context));
        assertEquals(123, wrapper.getPriority());
    }

    private RoutingContext routingContext(AtomicReference<Object> selectedMechanism) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(context.normalizedPath()).thenReturn("/login");
        when(context.request()).thenReturn(request);
        when(context.get(AUTHENTICATION_MECHANISM_KEY)).thenAnswer(invocation -> selectedMechanism.get());
        when(context.put(eq(AUTHENTICATION_MECHANISM_KEY), any())).thenAnswer(invocation -> {
            selectedMechanism.set(invocation.getArgument(1));
            return context;
        });
        return context;
    }

    private HillaFormAuthenticationMechanism mechanism(FormAuthenticationMechanism delegate) {
        return new HillaFormAuthenticationMechanism(
                delegate, new HillaFormAuthenticationMechanism.Config("auth", "/", "/logout", null, false));
    }
}
