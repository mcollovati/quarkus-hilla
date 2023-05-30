package com.github.mcollovati.quarkus.hilla;

import dev.hilla.push.PushEndpoint;
import javax.enterprise.inject.spi.CDI;
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
            U instance = defaultType.cast(
                    QuarkusApplicationContext.getBean(CDI.current().getBeanManager(), PushEndpoint.class));
            injectInjectable(instance, defaultType, config.framework());
            applyMethods(instance, defaultType);
            return instance;
        }
        return super.newClassInstance(classType, defaultType);
    }
}
