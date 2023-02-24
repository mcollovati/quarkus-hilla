package com.example.application.hillaextension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hilla.Endpoint;
import dev.hilla.EndpointControllerConfiguration;
import dev.hilla.EndpointInvoker;
import dev.hilla.EndpointNameChecker;
import dev.hilla.EndpointProperties;
import dev.hilla.EndpointRegistry;
import dev.hilla.EndpointUtil;
import dev.hilla.ExplicitNullableTypeChecker;
import dev.hilla.auth.CsrfChecker;
import dev.hilla.auth.EndpointAccessChecker;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;

import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.quarkus.AnyLiteral;

@ApplicationScoped
public class QuarkusEndpointControllerConfiguration {

    private EndpointControllerConfiguration configuration;

    @Inject
    public QuarkusEndpointControllerConfiguration(EndpointProperties endpointProperties) {
        configuration = new EndpointControllerConfiguration(endpointProperties);
    }

    @Produces
    @ApplicationScoped
    @Named
    public EndpointNameChecker endpointNameChecker() {
        return configuration.endpointNameChecker();
    }

    /**
     * Registers a default {@link EndpointAccessChecker} bean instance.
     *
     * @param accessAnnotationChecker the access controlks checker to use
     * @return the default Vaadin endpoint access checker bean
     */
    @Produces
    @ApplicationScoped
    @Named
    public EndpointAccessChecker accessChecker(
            AccessAnnotationChecker accessAnnotationChecker) {
        return configuration.accessChecker(accessAnnotationChecker);
    }

    /**
     * Registers a default {@link AccessAnnotationChecker} bean instance.
     *
     * @return the default bean
     */
    @Produces
    @ApplicationScoped
    @Named
    public AccessAnnotationChecker accessAnnotationChecker() {
        return configuration.accessAnnotationChecker();
    }

    /**
     * Registers a default {@link CsrfChecker} bean instance.
     *
     * @param servletContext the servlet context
     * @return the default bean
     */
    @Produces
    @ApplicationScoped
    @Named
    public CsrfChecker csrfChecker(ServletContext servletContext) {
        return configuration.csrfChecker(servletContext);
    }

    /**
     * Registers a {@link ExplicitNullableTypeChecker} bean instance.
     *
     * @return the explicit nullable type checker
     */

    @Produces
    @ApplicationScoped
    @Named
    public ExplicitNullableTypeChecker typeChecker() {
        return configuration.typeChecker();
    }

    /**
     * Registers endpoint utility methods.
     *
     * @return the endpoint util class
     */
    @Produces
    @ApplicationScoped
    @Named
    public EndpointUtil endpointUtil() {
        return configuration.endpointUtil();
    }

    /**
     * Registers the endpoint registry.
     *
     * @param endpointNameChecker the name checker to use
     * @return the endpoint registry
     */
    @Produces
    @ApplicationScoped
    @Named
    public EndpointRegistry endpointRegistry(
            EndpointNameChecker endpointNameChecker) {
        return configuration.endpointRegistry(endpointNameChecker);
    }

    @Produces
    @ApplicationScoped
    @Named
    public EndpointInvoker endpointInvoker(ApplicationContext context, ObjectMapper objectMapper,
                                           ExplicitNullableTypeChecker explicitNullableTypeChecker,
                                           ServletContext servletContext, EndpointRegistry endpointRegistry) {
        return configuration.endpointInvoker(context, objectMapper, explicitNullableTypeChecker, servletContext, endpointRegistry);
    }

    private static class ApplicationContextMock implements InvocationHandler {

        private final BeanManager beanManager;

        public ApplicationContextMock(BeanManager beanManager) {
            this.beanManager = beanManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("getBeansWithAnnotation") && args.length == 1) {
                return getBeansWithAnnotation((Class<? extends Annotation>) args[0]);
            } else if (method.getName().equals("getBean") && args.length == 1) {
                return getBean((Class<?>) args[0]);
            }
            return method.invoke(proxy, args);
        }


        Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
            // Only handle endpoint
            if (Endpoint.class.equals(annotationType)) {
                return beanManager.getBeans(Object.class, new AnyLiteral())
                //return beanManager.getBeans(Object.class, new EndpointLiteral())
                        .stream().filter(b -> b.getBeanClass().isAnnotationPresent(Endpoint.class))
                        .collect(Collectors.toMap(
                                Bean::getName, bean -> beanReference(bean, bean.getBeanClass())));
            }
            return Map.of();
        }

        <T> T getBean(Class<T> requiredType) throws BeansException {
            Set<Bean<?>> beans = beanManager.getBeans(requiredType, new AnyLiteral());
            if (beans.isEmpty()) {
                throw new NoSuchBeanDefinitionException(requiredType);
            }
            final Bean<?> bean;
            try {
                bean = beanManager.resolve(beans);
            } catch (final AmbiguousResolutionException e) {
                throw new NoUniqueBeanDefinitionException(requiredType, beans.stream().map(Bean::getName).collect(Collectors.toList()));
            }
            return beanReference(bean, requiredType);
        }

        private <T> T beanReference(Bean<?> bean, Class<T> requiredType) {
            final CreationalContext<?> ctx = beanManager
                    .createCreationalContext(bean);
            // noinspection unchecked
            return (T) beanManager.getReference(bean, requiredType, ctx);
        }


    }

    @Produces
    @ApplicationScoped
    @Named
    ApplicationContext mockApplicationContext(BeanManager beanManager) {
        return (ApplicationContext) Proxy.newProxyInstance(beanManager.getClass().getClassLoader(), new Class[]{ApplicationContext.class},
                new ApplicationContextMock(beanManager));

        /*
        return new ApplicationContext() {
            @Override
            public String getId() {
                return null;
            }

            @Override
            public String getApplicationName() {
                return null;
            }

            @Override
            public String getDisplayName() {
                return null;
            }

            @Override
            public long getStartupDate() {
                return 0;
            }

            @Override
            public ApplicationContext getParent() {
                return null;
            }

            @Override
            public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
                return null;
            }

            @Override
            public BeanFactory getParentBeanFactory() {
                return null;
            }

            @Override
            public boolean containsLocalBean(String name) {
                return false;
            }

            @Override
            public boolean containsBeanDefinition(String beanName) {
                return false;
            }

            @Override
            public int getBeanDefinitionCount() {
                return 0;
            }

            @Override
            public String[] getBeanDefinitionNames() {
                return new String[0];
            }

            @Override
            public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
                return null;
            }

            @Override
            public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
                return null;
            }

            @Override
            public String[] getBeanNamesForType(ResolvableType type) {
                return new String[0];
            }

            @Override
            public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
                return new String[0];
            }

            @Override
            public String[] getBeanNamesForType(Class<?> type) {
                return new String[0];
            }

            @Override
            public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
                return new String[0];
            }

            @Override
            public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
                return null;
            }

            @Override
            public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
                return null;
            }

            @Override
            public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
                return new String[0];
            }

            @Override
            public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
                // Only handle endpoint
                if (Endpoint.class.equals(annotationType)) {
                    return beanManager.getBeans(Object.class, new AnnotationLiteral<Endpoint>() {
                    }).stream().collect(Collectors.toMap(
                            Bean::getName, bean -> beanReference(bean, bean.getBeanClass())));
                }
                return Map.of();
            }

            @Override
            public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
                return null;
            }

            @Override
            public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
                return null;
            }

            @Override
            public Object getBean(String name) throws BeansException {
                return null;
            }

            @Override
            public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
                return null;
            }

            @Override
            public Object getBean(String name, Object... args) throws BeansException {
                return null;
            }

            @Override
            public <T> T getBean(Class<T> requiredType) throws BeansException {
                Set<Bean<?>> beans = beanManager.getBeans(requiredType);
                if (beans.isEmpty()) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                final Bean<?> bean;
                try {
                    bean = beanManager.resolve(beans);
                } catch (final AmbiguousResolutionException e) {
                    throw new NoUniqueBeanDefinitionException(requiredType, beans.stream().map(Bean::getName).collect(Collectors.toList()));
                }
                return beanReference(bean, requiredType);
            }

            private <T> T beanReference(Bean<?> bean, Class<T> requiredType) {
                final CreationalContext<?> ctx = beanManager
                        .createCreationalContext(bean);
                // noinspection unchecked
                return (T) beanManager.getReference(bean, requiredType, ctx);
            }

            @Override
            public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
                return null;
            }

            @Override
            public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
                return null;
            }

            @Override
            public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
                return null;
            }

            @Override
            public boolean containsBean(String name) {
                return false;
            }

            @Override
            public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
                return false;
            }

            @Override
            public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
                return false;
            }

            @Override
            public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
                return false;
            }

            @Override
            public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
                return false;
            }

            @Override
            public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
                return null;
            }

            @Override
            public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
                return null;
            }

            @Override
            public String[] getAliases(String name) {
                return new String[0];
            }

            @Override
            public void publishEvent(Object event) {

            }

            @Override
            public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
                return null;
            }

            @Override
            public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
                return null;
            }

            @Override
            public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
                return null;
            }

            @Override
            public Environment getEnvironment() {
                return null;
            }

            @Override
            public Resource[] getResources(String locationPattern) throws IOException {
                return new Resource[0];
            }

            @Override
            public Resource getResource(String location) {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return null;
            }
        };
         */
    }

}
