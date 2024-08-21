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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServletContext;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.hilla.EndpointCodeGenerator;
import com.vaadin.hilla.EndpointController;
import com.vaadin.hilla.EndpointInvoker;
import com.vaadin.hilla.EndpointNameChecker;
import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.EndpointUtil;
import com.vaadin.hilla.ExplicitNullableTypeChecker;
import com.vaadin.hilla.auth.CsrfChecker;
import com.vaadin.hilla.auth.EndpointAccessChecker;
import com.vaadin.hilla.parser.jackson.JacksonObjectMapperFactory;
import com.vaadin.hilla.route.ClientRouteRegistry;
import com.vaadin.hilla.route.RouteUnifyingConfigurationProperties;
import com.vaadin.hilla.route.RouteUtil;
import com.vaadin.hilla.startup.EndpointRegistryInitializer;
import com.vaadin.hilla.startup.RouteUnifyingServiceInitListener;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    @Singleton
    @DefaultBean
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
        class Factory extends JacksonObjectMapperFactory.Json {
            @Override
            @SuppressWarnings("deprecation")
            public ObjectMapper build() {
                // Emulate Spring default configuration
                return super.build()
                        .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            }
        }
        return new Factory();
    }

    @Produces
    @Singleton
    ApplicationContext applicationContext(BeanManager beanManager) {
        return new QuarkusApplicationContext(beanManager);
    }

    private EndpointController endpointController;

    @Produces
    @Singleton
    @DefaultBean
    EndpointController endpointController(
            ApplicationContext context,
            EndpointRegistry endpointRegistry,
            EndpointInvoker endpointInvoker,
            CsrfChecker csrfChecker) {
        this.endpointController = new EndpointController(context, endpointRegistry, endpointInvoker, csrfChecker);
        return this.endpointController;
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    EndpointCodeGenerator endpointCodeGenerator(ServletContext servletContext, EndpointController endpointController) {
        return new EndpointCodeGenerator(new VaadinServletContext(servletContext), endpointController);
    }

    @Produces
    @Singleton
    @DefaultBean
    ClientRouteRegistry clientRouteRegistry() {
        return new ClientRouteRegistry();
    }

    @Produces
    @Singleton
    @DefaultBean
    RouteUtil routeUtil(ClientRouteRegistry registry) {
        return new RouteUtil(registry);
    }

    @Produces
    @Singleton
    RouteUnifyingServiceInitListener routeUnifyingServiceInitListener(
            @ConfigProperty(name = "exposeServerRoutesToClient", defaultValue = "true")
                    boolean exposeServerRoutesToClient,
            RouteUtil routeUtil,
            ClientRouteRegistry clientRouteRegistry,
            Instance<NavigationAccessControl> navigationAccessControlInstance) {
        RouteUnifyingConfigurationProperties routeUnifyingConfigurationProperties =
                new RouteUnifyingConfigurationProperties();
        routeUnifyingConfigurationProperties.setExposeServerRoutesToClient(exposeServerRoutesToClient);
        NavigationAccessControl navigationAccessControl =
                navigationAccessControlInstance.isResolvable() ? navigationAccessControlInstance.get() : null;
        return new RouteUnifyingServiceInitListener(
                clientRouteRegistry, routeUtil, routeUnifyingConfigurationProperties, navigationAccessControl, null);
    }

    @Startup
    void initializeEndpointRegistry() {
        new EndpointRegistryInitializer(this.endpointController).serviceInit(vaadinServiceInitEvent);
        this.vaadinServiceInitEvent = null;
    }

    private ServiceInitEvent vaadinServiceInitEvent;

    void onVaadinServiceInit(ServiceInitEvent event) {
        this.vaadinServiceInitEvent = event;
    }
}
