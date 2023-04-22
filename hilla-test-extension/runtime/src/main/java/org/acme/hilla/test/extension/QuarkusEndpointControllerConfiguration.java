package org.acme.hilla.test.extension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletContext;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hilla.ByteArrayModule;
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
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.Startup;
import org.springframework.context.ApplicationContext;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;

@Unremovable
class QuarkusEndpointControllerConfiguration {

    /*
    private EndpointControllerConfiguration configuration;

    @Inject
    QuarkusEndpointControllerConfiguration(
            EndpointProperties endpointProperties) {
        configuration = new EndpointControllerConfiguration(endpointProperties);
    }
     */

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
    EndpointAccessChecker accessChecker(
            AccessAnnotationChecker accessAnnotationChecker) {
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
    EndpointInvoker endpointInvoker(ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            ExplicitNullableTypeChecker explicitNullableTypeChecker,
            ServletContext servletContext, EndpointRegistry endpointRegistry) {
        objectMapper.setVisibility(PropertyAccessor.ALL,
                JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new ByteArrayModule());
        return new EndpointInvoker(applicationContext, objectMapper,
                explicitNullableTypeChecker, servletContext, endpointRegistry);
    }

    /*
     * @Produces
     * 
     * @ApplicationScoped PushMessageHandler pushMessageHandler(EndpointInvoker
     * invoker, ServletContext servletContext) { PushMessageHandler
     * pushMessageHandler = new QuarkusPushMessageHandler(invoker,
     * servletContext); return pushMessageHandler; }
     */

    // @Produces
    // @ApplicationScoped
    // @Startup
    /*
     * QuarkusPushEndpoint pushEndpoint(PushMessageHandler pushMessageHandler,
     * ObjectMapper objectMapper) { return new QuarkusPushEndpoint(objectMapper,
     * pushMessageHandler); }
     */

    @Produces
    @ApplicationScoped
    @Startup
    ApplicationContext mockApplicationContext(BeanManager beanManager,
            ServletContext servletContext) {
        ApplicationContext webAppCtx = new QuarkusApplicationContext(
                beanManager, servletContext);
        /*
         * servletContext.setAttribute(
         * WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,
         * webAppCtx);
         */
        return webAppCtx;
    }

    @Produces
    @ApplicationScoped
    @Startup
    EndpointController endpointController(ApplicationContext context,
            EndpointRegistry endpointRegistry, EndpointInvoker endpointInvoker,
            CsrfChecker csrfChecker) {
        return new EndpointController(context, endpointRegistry,
                endpointInvoker, csrfChecker);
    }

}
