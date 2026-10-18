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
package com.github.mcollovati.quarkus.hilla.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.security.identity.SecurityIdentity;

/**
 * Configures navigation access control. Applications can provide a singleton
 * producer of this class to replace the default configuration.
 * <p>
 * An application configurer selects checkers explicitly. The extension's default
 * configurer selects all available CDI checkers. For example:
 * <pre>{@code
 * @Produces
 * @Singleton
 * NavigationAccessControlConfigurer navigationAccessControlConfigurer() {
 *     return new NavigationAccessControlConfigurer()
 *             .withAnnotatedViewAccessChecker();
 * }
 * }</pre>
 * Configurer producers, checkers and resolvers must not use runtime-init
 * synthetic beans during construction.
 *
 * @since 25.3.0
 */
public final class NavigationAccessControlConfigurer {

    private final List<NavigationAccessChecker> additionalCheckers = new ArrayList<>();
    private boolean annotatedViewAccessChecker;
    private boolean disabled;
    private Predicate<NavigationAccessChecker> availableCheckersFilter;
    private AccessCheckDecisionResolver decisionResolver;
    private Class<? extends Component> loginView;
    private String loginUrl;

    /**
     * Creates a configurer with no checkers selected.
     */
    public NavigationAccessControlConfigurer() {}

    /**
     * Selects the available annotated view access checker, including subclasses
     * and CDI client proxies. Fails at startup if none is available.
     *
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withAnnotatedViewAccessChecker() {
        annotatedViewAccessChecker = true;
        return this;
    }

    /**
     * Adds a checker instance. An instance selected through multiple methods
     * is used only once; distinct instances are kept even if they compare equal.
     *
     * @param checker the checker to add, not null
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withNavigationAccessChecker(NavigationAccessChecker checker) {
        additionalCheckers.add(Objects.requireNonNull(checker, "Navigation access checker must not be null"));
        return this;
    }

    /**
     * Adds checker instances.
     *
     * @param checkers the checkers to add, with no null elements
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withNavigationAccessCheckers(
            Collection<NavigationAccessChecker> checkers) {
        Objects.requireNonNull(checkers, "Navigation access checkers must not be null");
        checkers.forEach(this::withNavigationAccessChecker);
        return this;
    }

    /**
     * Selects available CDI checkers matching the predicate. Replaces any
     * previously configured predicate. Use {@link Class#isInstance(Object)}
     * for type matching so subclasses and CDI client proxies are included.
     *
     * @param predicate the filter for available checkers, not null
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withAvailableNavigationAccessCheckers(
            Predicate<NavigationAccessChecker> predicate) {
        availableCheckersFilter =
                Objects.requireNonNull(predicate, "Navigation access checker filter must not be null");
        return this;
    }

    /**
     * Uses an explicit resolver instead of resolving the CDI resolver bean.
     *
     * @param resolver the resolver to use, not null
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withDecisionResolver(AccessCheckDecisionResolver resolver) {
        decisionResolver = Objects.requireNonNull(resolver, "Decision resolver must not be null");
        return this;
    }

    /**
     * Sets the Flow login view. Repeating the same value is allowed; setting a
     * different class or a URL afterwards fails immediately.
     *
     * @param view the login view class, not null
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withLoginView(Class<? extends Component> view) {
        Objects.requireNonNull(view, "Login view must not be null");
        if (loginUrl != null || (loginView != null && loginView != view)) {
            throw new IllegalStateException("Login view is already configured as " + configuredLoginView()
                    + "; cannot also set " + view.getName());
        }
        loginView = view;
        return this;
    }

    /**
     * Sets the login URL. For form login it must match
     * {@code quarkus.http.auth.form.login-page}. Repeating the same value is
     * allowed; setting a different URL or a class afterwards fails immediately.
     *
     * @param url the login URL, not null
     * @return this configurer
     */
    public NavigationAccessControlConfigurer withLoginView(String url) {
        Objects.requireNonNull(url, "Login URL must not be null");
        if (loginView != null || (loginUrl != null && !loginUrl.equals(url))) {
            throw new IllegalStateException(
                    "Login view is already configured as " + configuredLoginView() + "; cannot also set " + url);
        }
        loginUrl = url;
        return this;
    }

    /**
     * Disables navigation access control.
     *
     * @return this configurer
     */
    public NavigationAccessControlConfigurer disabled() {
        disabled = true;
        return this;
    }

    private String configuredLoginView() {
        return loginView != null ? loginView.getName() : loginUrl;
    }

    void configureLoginView(NavigationAccessControl accessControl, String formLoginPage) {
        if (loginUrl != null && formLoginPage != null && !loginUrl.equals(formLoginPage)) {
            throw new IllegalStateException("NavigationAccessControlConfigurer sets the login view " + loginUrl
                    + ", which conflicts with quarkus.http.auth.form.login-page=" + formLoginPage
                    + ". Set both to the same URL or remove withLoginView(String).");
        }
        if (loginView != null) {
            accessControl.setLoginView(loginView);
        } else if (loginUrl != null) {
            accessControl.setLoginView(loginUrl);
        }
    }

    QuarkusNavigationAccessControl build(
            List<NavigationAccessChecker> availableCheckers,
            Supplier<AccessCheckDecisionResolver> fallbackResolver,
            SecurityIdentity securityIdentity,
            boolean propertyEnabled) {
        List<NavigationAccessChecker> selected = new ArrayList<>();
        if (annotatedViewAccessChecker) {
            NavigationAccessChecker checker = availableCheckers.stream()
                    .filter(AnnotatedViewAccessChecker.class::isInstance)
                    .findFirst()
                    .orElseThrow(
                            () -> new IllegalStateException("No AnnotatedViewAccessChecker CDI bean is available for "
                                    + "withAnnotatedViewAccessChecker()"));
            selected.add(checker);
        }
        selected.addAll(additionalCheckers);
        if (availableCheckersFilter != null) {
            availableCheckers.stream().filter(availableCheckersFilter).forEach(selected::add);
        }
        Set<NavigationAccessChecker> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        selected.removeIf(checker -> !seen.add(checker));
        if (!disabled && propertyEnabled && selected.isEmpty()) {
            throw new IllegalStateException("Navigation access control has no selected checkers. Select at least one "
                    + "checker, call disabled(), or set vaadin.security.navigation-access-control.enabled=false.");
        }
        QuarkusNavigationAccessControl accessControl = new QuarkusNavigationAccessControl(
                List.copyOf(selected),
                decisionResolver != null ? decisionResolver : fallbackResolver.get(),
                securityIdentity);
        if (disabled) {
            accessControl.setEnabled(false);
        }
        return accessControl;
    }
}
