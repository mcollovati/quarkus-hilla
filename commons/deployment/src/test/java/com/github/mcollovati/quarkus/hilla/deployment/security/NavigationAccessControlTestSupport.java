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
package com.github.mcollovati.quarkus.hilla.deployment.security;

import java.lang.reflect.Field;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.AbstractRouteRegistry;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import com.github.mcollovati.quarkus.hilla.deployment.TestUtils;

final class NavigationAccessControlTestSupport {

    static QuarkusExtensionTest extension(Class<?>... classes) {
        return new QuarkusExtensionTest()
                .withConfigurationResource(
                        TestUtils.class.getPackageName().replace('.', '/') + "/test-application.properties")
                .overrideConfigKey("quarkus.http.auth.form.enabled", "true")
                .overrideConfigKey("quarkus.http.auth.form.login-page", "/login")
                .overrideConfigKey("quarkus.http.test-port", "0")
                .setArchiveProducer(() -> archive(classes));
    }

    static JavaArchive archive(Class<?>... classes) {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(
                        TestUtils.class,
                        NavigationAccessControlTestSupport.class,
                        TestView.class,
                        TestRouteRegistry.class)
                .addClasses(classes);
    }

    static NavigationAccessControl accessControl() {
        return Arc.container().instance(NavigationAccessControl.class).get();
    }

    static NavigationContext context() {
        return new NavigationContext(
                new Router(new TestRouteRegistry()),
                TestView.class,
                new Location("protected"),
                RouteParameters.empty(),
                null,
                role -> false,
                false,
                false);
    }

    static class TestRouteRegistry extends AbstractRouteRegistry {
        @Override
        public VaadinContext getContext() {
            return null;
        }
    }

    static Object field(Object instance, Class<?> declaringClass, String name) throws ReflectiveOperationException {
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    @Tag("div")
    static class TestView extends Component {}
}
