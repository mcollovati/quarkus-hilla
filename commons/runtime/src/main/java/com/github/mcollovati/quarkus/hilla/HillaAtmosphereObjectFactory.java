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

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import java.lang.reflect.Field;
import java.util.ServiceLoader;

import com.vaadin.hilla.push.PushEndpoint;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.inject.InjectableObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HillaAtmosphereObjectFactory extends InjectableObjectFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HillaAtmosphereObjectFactory.class);

    static final String INJECTABLE_SERVICE_LOADER_FIELD = "injectableServiceLoader";

    private AtmosphereConfig config;

    public void configure(AtmosphereConfig config) {
        this.config = config;
        super.configure(config);
        releaseInjectableServiceLoader();
    }

    /*
     * InjectableObjectFactory iterates a ServiceLoader<Injectable> in
     * configure() and never uses it again. After the iteration the loader still
     * references its lazy class path lookup iterator, which keeps the class
     * loader resource enumeration it was created from. In native builds the
     * push servlet is configured at static init, so that enumeration ends up in
     * the image heap and the build fails with "Type is not available in this
     * platform: com.oracle.svm.hosted.NativeImageClassLoader$1". Reloading the
     * loader drops the iterator and the enumeration.
     */
    private void releaseInjectableServiceLoader() {
        try {
            Field field = InjectableObjectFactory.class.getDeclaredField(INJECTABLE_SERVICE_LOADER_FIELD);
            field.setAccessible(true);
            if (field.get(this) instanceof ServiceLoader<?> serviceLoader) {
                serviceLoader.reload();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Not fatal in JVM mode, but native builds may fail again: make it visible
            LOGGER.warn("Cannot reset the Atmosphere Injectable ServiceLoader", e);
        }
    }

    @Override
    public <T, U extends T> U newClassInstance(Class<T> classType, Class<U> defaultType)
            throws InstantiationException, IllegalAccessException {
        if (PushEndpoint.class.equals(defaultType)) {
            BeanManager beanManager = CDI.current().getBeanManager();
            U instance = defaultType.cast(QuarkusApplicationContext.getBean(beanManager, PushEndpoint.class));
            injectInjectable(instance, defaultType, config.framework());
            applyMethods(instance, defaultType);
            return instance;
        }
        return super.newClassInstance(classType, defaultType);
    }
}
