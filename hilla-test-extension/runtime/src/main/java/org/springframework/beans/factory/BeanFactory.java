package org.springframework.beans.factory;

import org.springframework.beans.BeansException;

public interface BeanFactory {

    <T> T getBean(Class<T> requiredType) throws BeansException;

    <T> T getBean(Class<T> requiredType, Object... args) throws BeansException;
}
