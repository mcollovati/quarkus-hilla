package org.springframework.web.context.support;

import javax.servlet.ServletContext;

import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.lang.Nullable;
import org.springframework.web.context.WebApplicationContext;

public abstract class WebApplicationContextUtils {

    private static WebApplicationContext appCtx;

    public static WebApplicationContext getWebApplicationContext(ServletContext sc) {
        return appCtx;
    }


    public static void initWebApplicationContext(ApplicationContext appCtx) {
        WebApplicationContextUtils.appCtx = new WebApplicationContextStub(appCtx);
    }

    private static class WebApplicationContextStub implements WebApplicationContext {

        @Override
        @Nullable
        public String getId() {
            return appCtx.getId();
        }

        @Override
        public String getApplicationName() {
            return appCtx.getApplicationName();
        }

        @Override
        public String getDisplayName() {
            return appCtx.getDisplayName();
        }

        @Override
        public long getStartupDate() {
            return appCtx.getStartupDate();
        }

        @Override
        @Nullable
        public ApplicationContext getParent() {
            return appCtx.getParent();
        }

        @Override
        public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
            return appCtx.getAutowireCapableBeanFactory();
        }

        @Override
        public <T> T getBean(Class<T> requiredType) throws BeansException {
            return appCtx.getBean(requiredType);
        }

        @Override
        public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
            return appCtx.getBean(requiredType, args);
        }

        @Override
        public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
            return appCtx.getBeansWithAnnotation(annotationType);
        }

        @Override
        @Nullable
        public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
            return appCtx.getMessage(code, args, defaultMessage, locale);
        }

        @Override
        public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
            return appCtx.getMessage(code, args, locale);
        }

        @Override
        public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
            return appCtx.getMessage(resolvable, locale);
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
            appCtx.publishEvent(event);
        }

        @Override
        public void publishEvent(Object event) {
            appCtx.publishEvent(event);
        }

        private final ApplicationContext appCtx;

        public WebApplicationContextStub(ApplicationContext appCtx) {
            this.appCtx = appCtx;
        }

        @Override
        public ServletContext getServletContext() {
            return null;
        }
    }

}
