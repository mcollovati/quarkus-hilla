/*
 * Copyright 2024 Marco Collovati, Dario Götze
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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;

import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereServlet;

import com.github.mcollovati.quarkus.hilla.graal.AtmosphereDeferredInitializer;

public class QuarkusAtmosphereServlet extends AtmosphereServlet {

    public QuarkusAtmosphereServlet() {
        super(false, false);
    }

    protected AtmosphereServlet configureFramework(ServletConfig sc, boolean init) throws ServletException {
        AtmosphereFramework framework = initializer
                .configureFramework(sc, true, false, QuarkusAtmosphereFramework.class)
                .framework();
        AtmosphereDeferredInitializer.register(sc, framework);
        return this;
    }

    public static class QuarkusAtmosphereFramework extends AtmosphereFramework {

        public QuarkusAtmosphereFramework(boolean isFilter, boolean autoDetectHandlers) {
            super(isFilter, autoDetectHandlers);
        }

        @Override
        protected void analytics() {}
    }
}
