package org.acme.hilla.test.extension;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.AmbiguousResolutionException;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;

import com.vaadin.quarkus.AnyLiteral;

class QuarkusApplicationContext implements ApplicationContext {

    private final BeanManager beanManager;
    QuarkusApplicationContext(BeanManager beanManager) {
        this.beanManager = beanManager;
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

    @Override
    public Environment getEnvironment() {
        return throwUnsupported();
    }

    @Override
    public BeanFactory getParentBeanFactory() {
        return throwUnsupported();
    }

    @Override
    public boolean containsLocalBean(String s) {
        return throwUnsupported();
    }

    @Override
    public boolean containsBeanDefinition(String s) {
        return throwUnsupported();
    }

    @Override
    public int getBeanDefinitionCount() {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> aClass, boolean b) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType,
            boolean b) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType resolvableType) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType resolvableType,
            boolean b, boolean b1) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(Class<?> aClass) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(Class<?> aClass, boolean b,
            boolean b1) {
        return throwUnsupported();
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> aClass)
            throws BeansException {
        return throwUnsupported();
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> aClass, boolean b,
            boolean b1) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForAnnotation(
            Class<? extends Annotation> aClass) {
        return throwUnsupported();
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(String s,
            Class<A> aClass) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(String s,
            Class<A> aClass, boolean b) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public Object getBean(String s) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public <T> T getBean(String s, Class<T> aClass) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public Object getBean(String s, Object... objects) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> aClass) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(
            ResolvableType resolvableType) {
        return throwUnsupported();
    }

    @Override
    public boolean containsBean(String s) {
        return throwUnsupported();
    }

    @Override
    public boolean isSingleton(String s) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isPrototype(String s) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isTypeMatch(String s, ResolvableType resolvableType)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isTypeMatch(String s, Class<?> aClass)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public Class<?> getType(String s) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public Class<?> getType(String s, boolean b)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public String[] getAliases(String s) {
        return throwUnsupported();
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        return throwUnsupported();
    }

    @Override
    public Resource getResource(String location) {
        return throwUnsupported();
    }

    @Override
    public ClassLoader getClassLoader() {
        return throwUnsupported();
    }
}
