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

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
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

class QuarkusApplicationContext implements ApplicationContext {

    private final BeanManager beanManager;

    QuarkusApplicationContext(BeanManager beanManager) {
        this.beanManager = beanManager;
    }

    private static <T> T beanReference(BeanManager beanManager, Bean<?> bean, Class<T> requiredType) {
        final CreationalContext<?> ctx = beanManager.createCreationalContext(bean);
        return requiredType.cast(beanManager.getReference(bean, requiredType, ctx));
    }

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return getBean(beanManager, requiredType);
    }

    static <T> T getBean(BeanManager beanManager, Class<T> requiredType) {
        Set<Bean<?>> beans = beanManager.getBeans(requiredType, new AnyLiteral());
        if (beans.isEmpty()) {
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        final Bean<?> bean;
        try {
            bean = beanManager.resolve(beans);
        } catch (final AmbiguousResolutionException e) {
            throw new NoUniqueBeanDefinitionException(
                    requiredType, beans.stream().map(Bean::getName).collect(Collectors.toList()));
        }
        return beanReference(beanManager, bean, requiredType);
    }

    @Override
    public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public Object getBean(String name) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public Object getBean(String name, Object... args) throws BeansException {
        return throwUnsupported();
    }

    @Override
    public boolean containsBean(String name) {
        return throwUnsupported();
    }

    @Override
    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public String[] getAliases(String name) throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
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
    public String[] getBeanNamesForType(ResolvableType type) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        return throwUnsupported();
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        return throwUnsupported();
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        return beanManager.getBeans(type, new AnyLiteral()).stream()
                .collect(Collectors.toMap(
                        QuarkusApplicationContext::computeBeanName, bean -> beanReference(beanManager, bean, type)));
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
            throws BeansException {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType resolvableType, boolean allowEagerInit) {
        return throwUnsupported();
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(org.springframework.core.ParameterizedTypeReference<T> requiredType) {
        return throwUnsupported();
    }

    @Override
    public BeanFactory getParentBeanFactory() {
        return throwUnsupported();
    }

    @Override
    public boolean containsLocalBean(String name) {
        return throwUnsupported();
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
            throws BeansException {
        return beanManager.getBeans(Object.class, new AnyLiteral()).stream()
                .filter(b -> b.getBeanClass().isAnnotationPresent(annotationType))
                .collect(Collectors.toMap(
                        QuarkusApplicationContext::computeBeanName,
                        bean -> beanReference(beanManager, bean, bean.getBeanClass())));
    }

    @Override
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        return throwUnsupported();
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public <A extends Annotation> A findAnnotationOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public <A extends Annotation> Set<A> findAllAnnotationsOnBean(
            String beanName, Class<A> annotationType, boolean allowFactoryBeanInit)
            throws NoSuchBeanDefinitionException {
        return throwUnsupported();
    }

    @Override
    public void publishEvent(Object event) {
        throwUnsupported();
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        return throwUnsupported();
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        return throwUnsupported();
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
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
    public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return throwUnsupported();
    }

    @Override
    public Environment getEnvironment() {
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

    private static String computeBeanName(Bean<?> bean) {
        String name = bean.getName();
        if (name == null) {
            return bean.getBeanClass().getSimpleName();
        }
        return name;
    }

    private static <T> T throwUnsupported() {
        throw new UnsupportedOperationException("Not implemented for Quarkus");
    }
}
