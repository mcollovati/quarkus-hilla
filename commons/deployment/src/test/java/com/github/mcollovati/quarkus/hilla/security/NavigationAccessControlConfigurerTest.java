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

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.AbstractRouteRegistry;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.AnnotatedViewAccessChecker;
import com.vaadin.flow.server.auth.DefaultAccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class NavigationAccessControlConfigurerTest {

    @Test
    void loginView_repeatedSameValueAccepted() {
        assertThat(new NavigationAccessControlConfigurer()
                        .withLoginView("/login")
                        .withLoginView("/login"))
                .isNotNull();
        assertThat(new NavigationAccessControlConfigurer()
                        .withLoginView(LoginView.class)
                        .withLoginView(LoginView.class))
                .isNotNull();
    }

    @Test
    void loginView_differentValuesFailImmediately() {
        var url = new NavigationAccessControlConfigurer().withLoginView("/login");
        assertThatIllegalStateException()
                .isThrownBy(() -> url.withLoginView("/other"))
                .withMessageContaining("/login")
                .withMessageContaining("/other");
        assertThatIllegalStateException()
                .isThrownBy(() -> url.withLoginView(LoginView.class))
                .withMessageContaining("/login")
                .withMessageContaining(LoginView.class.getName());
        var view = new NavigationAccessControlConfigurer().withLoginView(LoginView.class);
        assertThatIllegalStateException()
                .isThrownBy(() -> view.withLoginView("/login"))
                .withMessageContaining(LoginView.class.getName())
                .withMessageContaining("/login");
        assertThatIllegalStateException()
                .isThrownBy(() -> view.withLoginView(OtherLoginView.class))
                .withMessageContaining(LoginView.class.getName())
                .withMessageContaining(OtherLoginView.class.getName());
    }

    @Test
    void annotatedChecker_unavailableAcceptedWhenDisabled() {
        assertThat(new NavigationAccessControlConfigurer()
                        .withAnnotatedViewAccessChecker()
                        .disabled()
                        .build(List.of(), DefaultAccessCheckDecisionResolver::new, null, true)
                        .isEnabled())
                .isFalse();
        assertThat(new NavigationAccessControlConfigurer()
                        .withAnnotatedViewAccessChecker()
                        .build(List.of(), DefaultAccessCheckDecisionResolver::new, null, false))
                .isNotNull();
    }

    @Test
    void annotatedChecker_unavailableFailsClearly() {
        assertThatIllegalStateException()
                .isThrownBy(() -> new NavigationAccessControlConfigurer()
                        .withAnnotatedViewAccessChecker()
                        .build(List.of(), DefaultAccessCheckDecisionResolver::new, null, true))
                .withMessageContaining("No AnnotatedViewAccessChecker CDI bean is available");
    }

    @Test
    void annotatedChecker_subclassSelected() {
        var accessControl = new NavigationAccessControlConfigurer()
                .withAnnotatedViewAccessChecker()
                .build(List.of(new CustomAnnotatedChecker()), DefaultAccessCheckDecisionResolver::new, null, true);
        assertThat(accessControl.hasAccessChecker(CustomAnnotatedChecker.class)).isTrue();
    }

    @Test
    void availableCheckers_equalButDistinctInstancesAreBothUsed() {
        EqualChecker allow = new EqualChecker(true);
        EqualChecker deny = new EqualChecker(false);
        var accessControl = new NavigationAccessControlConfigurer()
                .withAvailableNavigationAccessCheckers(checker -> true)
                .withDecisionResolver((results, context) -> {
                    assertThat(results).containsExactly(AccessCheckResult.allow(), AccessCheckResult.deny("Denied"));
                    return context.deny("Both checkers used");
                })
                .build(List.of(allow, deny), DefaultAccessCheckDecisionResolver::new, null, true);
        var context = new NavigationContext(
                new Router(new AbstractRouteRegistry() {
                    @Override
                    public VaadinContext getContext() {
                        return null;
                    }
                }),
                LoginView.class,
                new Location("protected"),
                RouteParameters.empty(),
                null,
                role -> false,
                false,
                false);
        assertThat(accessControl.checkAccess(context, false).reason()).isEqualTo("Both checkers used");
    }

    private record EqualChecker(boolean allow) implements NavigationAccessChecker {
        @Override
        public AccessCheckResult check(NavigationContext context) {
            return allow ? context.allow() : context.deny("Denied");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualChecker;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    @Tag("div")
    static class LoginView extends Component {}

    @Tag("div")
    static class OtherLoginView extends Component {}

    static class CustomAnnotatedChecker extends AnnotatedViewAccessChecker {}
}
