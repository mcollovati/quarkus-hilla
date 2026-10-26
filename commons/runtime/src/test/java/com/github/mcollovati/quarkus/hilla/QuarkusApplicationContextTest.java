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
package com.github.mcollovati.quarkus.hilla;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkusApplicationContextTest {

    @Test
    void getBeanProvider_availableBean_returnsReference() {
        BeanManager beanManager = mock(BeanManager.class);
        Bean<?> bean = mock(Bean.class);
        Service service = new Service();
        when(beanManager.getBeans(eq(Service.class), any())).thenReturn(Set.of(bean));
        doResolve(beanManager, bean);
        when(beanManager.createCreationalContext(bean)).thenReturn(mock(CreationalContext.class));
        when(beanManager.getReference(eq(bean), eq(Service.class), any())).thenReturn(service);

        var provider = new QuarkusApplicationContext(beanManager).getBeanProvider(Service.class);

        assertSame(service, provider.getIfAvailable());
        assertSame(service, provider.getObject());
    }

    @Test
    void getBeanProvider_missingBean_isNotAvailable() {
        BeanManager beanManager = mock(BeanManager.class);
        when(beanManager.getBeans(eq(Service.class), any())).thenReturn(Set.of());

        var provider = new QuarkusApplicationContext(beanManager).getBeanProvider(Service.class);

        assertNull(provider.getIfAvailable());
        assertNull(provider.getIfUnique());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void doResolve(BeanManager beanManager, Bean<?> bean) {
        when(beanManager.resolve((Set) any())).thenReturn((Bean) bean);
    }

    static class Service {}
}
