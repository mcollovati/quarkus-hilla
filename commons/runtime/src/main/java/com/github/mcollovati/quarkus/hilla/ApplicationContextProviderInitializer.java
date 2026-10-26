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

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import com.vaadin.hilla.ApplicationContextProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import org.springframework.context.ApplicationContext;

/**
 * Hands the Quarkus-backed {@link ApplicationContext} to Hilla's {@link ApplicationContextProvider} when the servlet
 * context is initialized, before any servlet starts. This is the Quarkus counterpart of the
 * {@code ApplicationContextAware} callback Spring invokes on the provider.
 * <p>
 * It must not happen inside the {@link ApplicationContext} producer: {@code setApplicationContext} runs the actions
 * queued through {@link ApplicationContextProvider#runOnContext}. In development mode one of them resolves the
 * {@code EndpointCodeGenerator} bean, which depends on {@code EndpointController}, which in turn depends on the
 * {@link ApplicationContext} that is still being produced, so ArC re-enters the producer until the stack overflows.
 */
public class ApplicationContextProviderInitializer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ArcContainer container = Arc.container();
        ApplicationContext applicationContext =
                container.instance(ApplicationContext.class).get();
        container.instance(ApplicationContextProvider.class).get().setApplicationContext(applicationContext);
    }
}
