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
package dev.codex.quarkushilla.copilot.app;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.Endpoint;
import com.vaadin.quarkus.annotation.NormalRouteScoped;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import com.vaadin.quarkus.annotation.VaadinServiceScoped;
import com.vaadin.quarkus.annotation.VaadinSessionScoped;

public final class CopilotTestBeans {

    private CopilotTestBeans() {}

    public static class AAlphabeticallyFirstHelper {}

    public static class ZzzAppShell implements AppShellConfigurator {}

    @ApplicationScoped
    public static class ApplicationScopedFlowService {

        public String applicationMethod() {
            return "application";
        }
    }

    @Singleton
    public static class SingletonFlowService {

        public String singletonMethod() {
            return "singleton";
        }
    }

    @Dependent
    public static class DependentFlowService {

        public String dependentMethod() {
            return "dependent";
        }
    }

    @RequestScoped
    public static class RequestScopedFlowService {

        public String requestMethod() {
            return "request";
        }
    }

    @VaadinServiceScoped
    public static class VaadinServiceScopedFlowService {

        public String vaadinServiceMethod() {
            return "vaadin-service";
        }
    }

    @VaadinSessionScoped
    public static class VaadinSessionScopedFlowService {

        public String vaadinSessionMethod() {
            return "vaadin-session";
        }
    }

    @NormalUIScoped
    public static class VaadinUiScopedFlowService {

        public String vaadinUiMethod() {
            return "vaadin-ui";
        }
    }

    @NormalRouteScoped
    public static class VaadinRouteScopedFlowService {

        public String vaadinRouteMethod() {
            return "vaadin-route";
        }
    }

    @BrowserCallable
    public static class BrowserCallableEndpoint {

        public String browserCallableMethod() {
            return "browser-callable";
        }
    }

    @Endpoint
    public static class LegacyEndpoint {

        public String endpointMethod() {
            return "endpoint";
        }
    }
}
