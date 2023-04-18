package org.acme.hilla.test.extension;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.servlet.ServletContext;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.web.context.WebApplicationContext;

import com.vaadin.quarkus.AnyLiteral;

class QuarkusApplicationContext implements WebApplicationContext {

    private final BeanManager beanManager;
    private final ServletContext servletContext;

    QuarkusApplicationContext(BeanManager beanManager,
            ServletContext servletContext) {
        this.beanManager = beanManager;
        this.servletContext = servletContext;
    }

    private static <T> T beanReference(BeanManager beanManager, Bean<?> bean,
            Class<T> requiredType) {
        final CreationalContext<?> ctx = beanManager
                .createCreationalContext(bean);
        // noinspection unchecked
        return (T) beanManager.getReference(bean, requiredType, ctx);
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return getBean(beanManager, requiredType);
    }

    static <T> T getBean(BeanManager beanManager, Class<T> requiredType) {
        Set<Bean<?>> beans = beanManager.getBeans(requiredType,
                new AnyLiteral());
        if (beans.isEmpty()) {
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        final Bean<?> bean;
        try {
            bean = beanManager.resolve(beans);
        } catch (final AmbiguousResolutionException e) {
            throw new NoUniqueBeanDefinitionException(requiredType, beans
                    .stream().map(Bean::getName).collect(Collectors.toList()));
        }
        return beanReference(beanManager, bean, requiredType);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(
            Class<? extends Annotation> annotationType) throws BeansException {
        return beanManager.getBeans(Object.class, new AnyLiteral())
                // return beanManager.getBeans(Object.class, new
                // EndpointLiteral())
                .stream()
                .filter(b -> b.getBeanClass()
                        .isAnnotationPresent(annotationType))
                .collect(Collectors.toMap(
                        QuarkusApplicationContext::computeBeanName,
                        bean -> beanReference(beanManager, bean,
                                bean.getBeanClass())));
    }

    private static String computeBeanName(Bean<?> bean) {
        String name = bean.getName();
        if (name == null) {
            return bean.getBeanClass().getName();
        }
        return name;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    private static <T> T throwUnsupported() {
        throw new UnsupportedOperationException("Not implemented for Quarkus");
    }

    @Override
    public <T> T getBean(Class<T> requiredType, Object... args)
            throws BeansException {
        return throwUnsupported();
    }

    @Override
    public String getId() {
        return throwUnsupported();
    }

    @Override
    public String getApplicationName() {
        return throwUnsupported();
    }

    @Override
    public String getDisplayName() {
        return throwUnsupported();
    }

    @Override
    public long getStartupDate() {
        return throwUnsupported();
    }

    @Override
    public ApplicationContext getParent() {
        return throwUnsupported();
    }

    @Override
    public AutowireCapableBeanFactory getAutowireCapableBeanFactory()
            throws IllegalStateException {
        return throwUnsupported();
    }

    @Override
    public void publishEvent(Object event) {
        throwUnsupported();
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage,
            Locale locale) {
        return throwUnsupported();
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale)
            throws NoSuchMessageException {
        return throwUnsupported();
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale)
            throws NoSuchMessageException {
        return throwUnsupported();
    }

}
