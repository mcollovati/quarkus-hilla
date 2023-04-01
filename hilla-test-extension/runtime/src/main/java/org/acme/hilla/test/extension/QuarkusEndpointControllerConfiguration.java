package org.acme.hilla.test.extension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hilla.EndpointController;
import dev.hilla.EndpointControllerConfiguration;
import dev.hilla.EndpointInvoker;
import dev.hilla.EndpointNameChecker;
import dev.hilla.EndpointProperties;
import dev.hilla.EndpointRegistry;
import dev.hilla.EndpointUtil;
import dev.hilla.ExplicitNullableTypeChecker;
import dev.hilla.auth.CsrfChecker;
import dev.hilla.auth.EndpointAccessChecker;
import dev.hilla.push.PushMessageHandler;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.Startup;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;

class QuarkusEndpointControllerConfiguration {

    private EndpointControllerConfiguration configuration;

    @Inject
    QuarkusEndpointControllerConfiguration(EndpointProperties endpointProperties) {
        configuration = new EndpointControllerConfiguration(endpointProperties);
    }

    @Produces
    @Singleton
    @DefaultBean
    EndpointNameChecker endpointNameChecker() {
        return configuration.endpointNameChecker();
    }

    /**
     * Registers a default {@link EndpointAccessChecker} bean instance.
     *
     * @param accessAnnotationChecker the access controlks checker to use
     * @return the default Vaadin endpoint access checker bean
     */
    @Produces
    @Singleton
    @DefaultBean
    EndpointAccessChecker accessChecker(
            AccessAnnotationChecker accessAnnotationChecker) {
        return configuration.accessChecker(accessAnnotationChecker);
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
        return configuration.accessAnnotationChecker();
    }

    /**
     * Registers a default {@link CsrfChecker} bean instance.
     *
     * @param servletContext the servlet context
     * @return the default bean
     */
    @Produces
    @Singleton
    @DefaultBean
    CsrfChecker csrfChecker(ServletContext servletContext) {
        return configuration.csrfChecker(servletContext);
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
        return configuration.typeChecker();
    }

    /**
     * Registers endpoint utility methods.
     *
     * @return the endpoint util class
     */
    @Produces
    @Singleton
    EndpointUtil endpointUtil() {
        return configuration.endpointUtil();
    }

    /**
     * Registers the endpoint registry.
     *
     * @param endpointNameChecker the name checker to use
     * @return the endpoint registry
     */
    @Produces
    @Singleton
    @DefaultBean
    EndpointRegistry endpointRegistry(
            EndpointNameChecker endpointNameChecker) {
        return configuration.endpointRegistry(endpointNameChecker);
    }

    @Produces
    @Singleton
    EndpointInvoker endpointInvoker(ApplicationContext context, ObjectMapper objectMapper,
                                    ExplicitNullableTypeChecker explicitNullableTypeChecker,
                                    ServletContext servletContext, EndpointRegistry endpointRegistry) {
        return configuration.endpointInvoker(context, objectMapper, explicitNullableTypeChecker, servletContext, endpointRegistry);
    }

    @Produces
    @Singleton
    PushMessageHandler pushMessageHandler(EndpointInvoker invoker, ServletContext servletContext) {
        PushMessageHandler pushMessageHandler = new QuarkusPushMessageHandler(invoker, servletContext);
        return pushMessageHandler;
    }

    @Produces
    @Singleton
    @Startup
    QuarkusPushEndpoint pushEndpoint(PushMessageHandler pushMessageHandler, ObjectMapper objectMapper) {
        return new QuarkusPushEndpoint(objectMapper, pushMessageHandler);
    }

    @Produces
    @Singleton
    @Startup
    WebApplicationContext mockApplicationContext(BeanManager beanManager, ServletContext servletContext) {
        WebApplicationContext webAppCtx = new QuarkusApplicationContext(beanManager, servletContext);
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webAppCtx);
        return webAppCtx;
    }

    @Produces
    @Singleton
    @Startup
    EndpointController endpointController(ApplicationContext context, EndpointRegistry endpointRegistry, EndpointInvoker endpointInvoker, CsrfChecker csrfChecker) {
        return new EndpointController(context, endpointRegistry, endpointInvoker, csrfChecker);
    }

}
