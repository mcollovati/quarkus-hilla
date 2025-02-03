/*
 * Copyright 2025 Marco Collovati, Dario Götze
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
package com.github.mcollovati.quarkus.hilla.security;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellRegistry;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.server.PwaConfiguration;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import org.slf4j.LoggerFactory;

/**
 * Matches request for custom PWA icons and Favicon paths.
 * <p>
 * PWA icon paths are computed by analyzing the {@link PWA} annotation on the
 * {@link AppShellConfigurator} implementor class. The favicon is detected by
 * invoking the {@link AppShellConfigurator#configurePage(AppShellSettings)}
 * method and tracking potential calls to
 * {@link AppShellSettings#addFavIcon(String, String, String)} and
 * {@link AppShellSettings#addFavIcon(String, String, String)} methods.
 * <p>
 * Default paths ({@link PwaConfiguration#DEFAULT_ICON} and
 * {@literal /favicon.ico}) are not considered.
 */
public class WebIconsRequestMatcher {

    private final ImmutablePathMatcher<Boolean> matcher;

    /**
     * Creates a new WebIconsRequestMatcher.
     *
     * @param service    VaadinService instance, not {@literal null}.
     * @param urlMapping Vaadin servlet url mapping, can be {@literal null}.
     */
    public WebIconsRequestMatcher(VaadinService service, String urlMapping) {
        matcher = initMatchers(service, urlMapping);
    }

    public boolean isWebIconRequest(String path) {
        final var match = matcher.match(path);
        return match.getValue() != null && match.getValue();
    }

    private static ImmutablePathMatcher<Boolean> initMatchers(VaadinService service, String urlMapping) {

        AppShellRegistry appShellRegistry = AppShellRegistry.getInstance(service.getContext());
        Class<? extends AppShellConfigurator> appShellClass = appShellRegistry.getShell();

        UnaryOperator<String> urlMapper = path -> PathUtil.applyUrlMapping(urlMapping, path);
        Set<String> paths = new HashSet<>();
        appendFavIconPath(paths, appShellClass, service, urlMapper);
        appendPwaIconPaths(paths, appShellClass, service, urlMapper);
        ImmutablePathMatcher.ImmutablePathMatcherBuilder<Boolean> pathMatcherBuilder = ImmutablePathMatcher.builder();
        paths.forEach(path -> pathMatcherBuilder.addPath(path, true));
        return pathMatcherBuilder.build();
    }

    private static void appendFavIconPath(
            Set<String> paths,
            Class<? extends AppShellConfigurator> appShellClass,
            VaadinService vaadinService,
            UnaryOperator<String> urlMapper) {
        if (appShellClass != null) {
            AppShellSettings settings = new AppShellSettings() {
                @Override
                public void addFavIcon(String rel, String href, String sizes) {
                    registerPath(href);
                }

                @Override
                public void addFavIcon(Inline.Position position, String rel, String href, String sizes) {
                    registerPath(href);
                }

                private void registerPath(String path) {
                    if (!path.startsWith("/")) {
                        path = urlMapper.apply(path);
                    }
                    paths.add(path);
                }
            };
            try {
                vaadinService.getInstantiator().getOrCreate(appShellClass).configurePage(settings);
            } catch (Exception ex) {
                LoggerFactory.getLogger(WebIconsRequestMatcher.class).debug("Cannot detect favicon path", ex);
            }
        }
        // Remove default favicon paths
        paths.remove("/favicon.ico");
    }

    private static void appendPwaIconPaths(
            Set<String> paths, Class<?> appShellClass, VaadinService vaadinService, UnaryOperator<String> urlMapper) {
        Optional.<Class<?>>ofNullable(appShellClass)
                // Otherwise use the class reported by router
                .or(() -> Optional.ofNullable(ApplicationRouteRegistry.getInstance(vaadinService.getContext())
                        .getPwaConfigurationClass()))
                .map(c -> c.getAnnotation(PWA.class))
                .map(PWA::iconPath)
                .filter(path -> !PwaConfiguration.DEFAULT_ICON.equals(path))
                .ifPresent(path -> {
                    // Base icon is not served by PwaHandler, so it is not aware of
                    // urlMapping
                    paths.add(PathUtil.ensureSlashBegin(path));

                    HandlerHelper.getIconVariants(path).stream().map(urlMapper).forEach(paths::add);
                });
    }
}
