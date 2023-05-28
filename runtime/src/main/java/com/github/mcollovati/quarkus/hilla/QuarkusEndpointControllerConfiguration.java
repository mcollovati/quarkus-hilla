/*
 * Copyright 2023 Marco Collovati, Dario Götze
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

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import dev.hilla.EndpointController;
import dev.hilla.EndpointInvoker;
import dev.hilla.EndpointNameChecker;
import dev.hilla.EndpointRegistry;
import dev.hilla.EndpointUtil;
import dev.hilla.ExplicitNullableTypeChecker;
import dev.hilla.auth.CsrfChecker;
import dev.hilla.auth.EndpointAccessChecker;
import dev.hilla.parser.jackson.JacksonObjectMapperFactory;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContext;
import org.springframework.context.ApplicationContext;

@Unremovable
class QuarkusEndpointControllerConfiguration {

    @Produces
    @Singleton
    @DefaultBean
    EndpointNameChecker endpointNameChecker() {
        return new EndpointNameChecker();
    }

    /**
     * Registers a default {@link EndpointAccessChecker} bean instance.
     *
     * @param accessAnnotationChecker
     *            the access controlks checker to use
     * @return the default Vaadin endpoint access checker bean
     */
    @Produces
    @Singleton
    @DefaultBean
    EndpointAccessChecker accessChecker(AccessAnnotationChecker accessAnnotationChecker) {
        return new EndpointAccessChecker(accessAnnotationChecker);
    }

    /**
     * Registers a default {@link AccessAnnotationChecker} bean instance.
     *
     * @return the default bean
     */
    @Produces
    @Singleton
    @DefaultBean
    AccessAnnotationChecker accessAnnotationChecker() {
        return new AccessAnnotationChecker();
    }

    /**
     * Registers a default {@link CsrfChecker} bean instance.
     *
     * @param servletContext
     *            the servlet context
     * @return the default bean
     */
    @Produces
    @ApplicationScoped
    @DefaultBean
    CsrfChecker csrfChecker(ServletContext servletContext) {
        return new CsrfChecker(servletContext);
    }

    /**
     * Registers a {@link ExplicitNullableTypeChecker} bean instance.
     *
     * @return the explicit nullable type checker
     */
    @Produces
    @Singleton
    @DefaultBean
    ExplicitNullableTypeChecker typeChecker() {
        return new ExplicitNullableTypeChecker();
    }

    /**
     * Registers endpoint utility methods.
     *
     * @return the endpoint util class
     */
    @Produces
    @Singleton
    EndpointUtil endpointUtil() {
        return new EndpointUtil();
    }

    /**
     * Registers the endpoint registry.
     *
     * @param endpointNameChecker
     *            the name checker to use
     * @return the endpoint registry
     */
    @Produces
    @Singleton
    @DefaultBean
    EndpointRegistry endpointRegistry(EndpointNameChecker endpointNameChecker) {
        return new EndpointRegistry(endpointNameChecker);
    }

    @Produces
    @ApplicationScoped
    EndpointInvoker endpointInvoker(
            ApplicationContext applicationContext,
            @Named(EndpointController.ENDPOINT_MAPPER_FACTORY_BEAN_QUALIFIER)
                    JacksonObjectMapperFactory objectMapperFactory,
            ExplicitNullableTypeChecker explicitNullableTypeChecker,
            ServletContext servletContext,
            EndpointRegistry endpointRegistry) {
        return new EndpointInvoker(
                applicationContext, objectMapperFactory, explicitNullableTypeChecker, servletContext, endpointRegistry);
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    @Named(EndpointController.ENDPOINT_MAPPER_FACTORY_BEAN_QUALIFIER)
    JacksonObjectMapperFactory objectMapperFactory() {
        return new JacksonObjectMapperFactory.Json();
    }

    @Produces
    @ApplicationScoped
    @Startup
    ApplicationContext applicationContext(BeanManager beanManager) {
        return new QuarkusApplicationContext(beanManager);
    }

    @Produces
    @ApplicationScoped
    @Startup
    EndpointController endpointController(
            ApplicationContext context,
            EndpointRegistry endpointRegistry,
            EndpointInvoker endpointInvoker,
            CsrfChecker csrfChecker) {
        EndpointController controller = new EndpointController(context, endpointRegistry, endpointInvoker, csrfChecker);
        controller.registerEndpoints();
        return controller;
    }
}
