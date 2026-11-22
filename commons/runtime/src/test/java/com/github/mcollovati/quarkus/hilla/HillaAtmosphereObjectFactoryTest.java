/*
 * Copyright 2026 Marco Collovati, Dario Götze
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

import java.lang.reflect.Field;
import java.util.ServiceLoader;

import org.atmosphere.inject.InjectableObjectFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HillaAtmosphereObjectFactoryTest {

    /*
     * HillaAtmosphereObjectFactory resets this private Atmosphere field after
     * configure() to keep a class loader resource enumeration out of the
     * native image heap. If an Atmosphere upgrade renames or retypes it, the
     * reset silently stops working and native builds fail again.
     */
    @Test
    void injectableServiceLoaderField_existsInAtmosphere() throws NoSuchFieldException {
        Field field = InjectableObjectFactory.class.getDeclaredField(
                HillaAtmosphereObjectFactory.INJECTABLE_SERVICE_LOADER_FIELD);

        assertEquals(ServiceLoader.class, field.getType());
    }
}
