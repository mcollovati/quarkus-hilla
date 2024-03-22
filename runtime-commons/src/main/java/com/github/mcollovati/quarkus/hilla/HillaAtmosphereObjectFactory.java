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

import com.vaadin.hilla.EndpointController;
import com.vaadin.hilla.push.PushEndpoint;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.inject.InjectableObjectFactory;

public class HillaAtmosphereObjectFactory extends InjectableObjectFactory {

    private AtmosphereConfig config;

    public void configure(AtmosphereConfig config) {
        this.config = config;
        super.configure(config);
    }

    @Override
    public <T, U extends T> U newClassInstance(Class<T> classType, Class<U> defaultType)
            throws InstantiationException, IllegalAccessException {
        if (PushEndpoint.class.equals(defaultType)) {
            BeanManager beanManager = CDI.current().getBeanManager();
            // ensure EndpointController gets initialized so that also EndpointRegistry is populated
            QuarkusApplicationContext.getBean(beanManager, EndpointController.class);
            U instance = defaultType.cast(QuarkusApplicationContext.getBean(beanManager, PushEndpoint.class));
            injectInjectable(instance, defaultType, config.framework());
            applyMethods(instance, defaultType);
            return instance;
        }
        return super.newClassInstance(classType, defaultType);
    }
}
