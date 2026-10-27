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
package com.github.mcollovati.quarkus.hilla.security;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.function.Supplier;

import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.VertxHttpConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.LoggerFactory;

import com.github.mcollovati.quarkus.hilla.QuarkusHillaExtension;

@Recorder
public class HillaSecurityRecorder {

    private static final String FORM_LOGIN_PAGE = "quarkus.http.auth.form.login-page";

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        return () -> {
            FormAuthenticationMechanism delegate = formAuthenticationMechanism();
            FormLoginSettings settings = formLoginSettings(delegate);
            return new HillaFormAuthenticationMechanism(delegate, logoutConfig(settings.landingPage()));
        };
    }

    /**
     * Creates the filter that handles requests to {@code vaadin.security.logout-path}.
     *
     * @return the logout filter
     */
    public Handler<RoutingContext> formLogoutHandler() {
        return new FormLogoutHandler(logoutConfig(formConfig().landingPage().orElse("/")));
    }

    public void configureHttpSecurityPolicy(BeanContainer container) {
        HillaSecurityPolicy policy = container.beanInstance(HillaSecurityPolicy.class);
        policy.withFormLogin(formLoginSettings(formAuthenticationMechanism()).publicPaths());
        markSecurityPolicyUsed();
    }

    // The CDI bean is the mechanism Quarkus uses, also when it is set up with HttpSecurity.
    private static FormAuthenticationMechanism formAuthenticationMechanism() {
        return Arc.container().instance(FormAuthenticationMechanism.class).get();
    }

    private static FormLoginSettings formLoginSettings(FormAuthenticationMechanism mechanism) {
        return FormLoginSettings.of(mechanism, formConfig());
    }

    private static FormAuthConfig formConfig() {
        return ConfigProvider.getConfig()
                .unwrap(SmallRyeConfig.class)
                .getConfigMapping(VertxHttpConfig.class)
                .auth()
                .form();
    }

    private static HillaFormAuthenticationMechanism.Config logoutConfig(String landingPage) {
        VaadinSecurityConfig securityConfig =
                ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getConfigMapping(VaadinSecurityConfig.class);
        FormAuthConfig formConfig = formConfig();
        return new HillaFormAuthenticationMechanism.Config(
                formConfig.cookieName(),
                landingPage,
                securityConfig.logoutPath(),
                securityConfig.postLogoutRedirectUri().orElse(null),
                securityConfig.logoutInvalidateSession(),
                formConfig.cookiePath().orElse("/"),
                formConfig.cookieDomain().orElse(null),
                formConfig.httpOnlyCookie(),
                CookieSameSite.valueOf(formConfig.cookieSameSite().name()));
    }

    /**
     * Applies startup login and opt-out settings to the contextual access control,
     * validating legacy replacements and configurer login settings first.
     *
     * @param container the CDI bean container
     * @param enabled whether navigation access control is permitted by the build-time configuration
     */
    public void configureNavigationAccessControl(BeanContainer container, boolean enabled) {
        boolean extensionAccessControl = isExtensionBean(NavigationAccessControl.class);
        if (extensionAccessControl) {
            InjectableBean<NavigationAccessControlConfigurer> configurerBean =
                    resolvedBean(NavigationAccessControlConfigurer.class);
            if (configurerBean.getScope() != Singleton.class) {
                // The access control is built from one configurer instance;
                // the login view has to be validated against the same one.
                throw new IllegalStateException("The NavigationAccessControlConfigurer bean "
                        + configurerBean.getBeanClass().getName() + " must be @Singleton.");
            }
        } else {
            if (!isExtensionBean(NavigationAccessControlConfigurer.class)) {
                throw new IllegalStateException("An application NavigationAccessControl bean and an application "
                        + "NavigationAccessControlConfigurer bean cannot be used together. Keep only the configurer.");
            }
            LoggerFactory.getLogger(HillaSecurityRecorder.class)
                    .warn("Replacing the NavigationAccessControl bean is deprecated and will be removed in 26.0. "
                            + "Provide a NavigationAccessControlConfigurer bean instead. "
                            + "If the configurer does not cover your use case, please describe it in an issue at "
                            + "https://github.com/mcollovati/quarkus-hilla/issues");
            InjectableBean<NavigationAccessControl> bean = resolvedBean(NavigationAccessControl.class);
            if (bean.getScope() == Dependent.class) {
                // Every injection point gets its own instance, so settings applied
                // here would not reach the instances that check navigation.
                String message = "The NavigationAccessControl bean "
                        + bean.getBeanClass().getName()
                        + " is @Dependent, so the extension cannot configure the instances in use. "
                        + "Declare it @Singleton or @ApplicationScoped.";
                if (!enabled) {
                    throw new IllegalStateException(message + " Otherwise vaadin.security.navigation-access-control"
                            + ".enabled=false has no effect.");
                }
                LoggerFactory.getLogger(HillaSecurityRecorder.class)
                        .warn("{} Otherwise {} is not applied as its login view.", message, FORM_LOGIN_PAGE);
                return;
            }
        }
        // Resolved by base type, an application may provide its own bean.
        // Unwrapped, so that a normal-scoped bean can be inspected.
        NavigationAccessControl accessControl =
                ClientProxy.unwrap(container.beanInstance(NavigationAccessControl.class));
        // Read at startup, as Quarkus does for its own form login redirect.
        var loginPage = Optional.ofNullable(
                formLoginSettings(formAuthenticationMechanism()).loginPage());
        if (extensionAccessControl) {
            container
                    .beanInstance(NavigationAccessControlConfigurer.class)
                    .configureLoginView(accessControl, loginPage.orElse(null));
        }
        // Names what set the login view in log and error messages.
        String owner = extensionAccessControl
                ? NavigationAccessControlConfigurer.class.getSimpleName()
                : accessControl.getClass().getName();
        loginPage.ifPresent(page -> applyLoginView(accessControl, owner, page));
        if (!enabled) {
            // Never turns the access control on, an application disabling its
            // own bean keeps that state.
            accessControl.setEnabled(false);
        }
    }

    // Checks the producer, not isDefaultBean(): an application may declare
    // its own default bean with a priority.
    private static boolean isExtensionBean(Class<?> type) {
        return resolvedBean(type).getBeanClass() == NavigationAccessControlProducer.class;
    }

    // Resolves the bean without creating an instance.
    private static <T> InjectableBean<T> resolvedBean(Class<T> type) {
        return Arc.container().select(type).getHandle().getBean();
    }

    private static void applyLoginView(NavigationAccessControl accessControl, String owner, String loginPage) {
        if (accessControl instanceof QuarkusNavigationAccessControl ours) {
            Class<?> loginViewClass = ours.currentLoginViewClass();
            String loginUrl = ours.currentLoginUrl();
            if (loginViewClass != null) {
                // The route of a login view class cannot be compared reliably
                // at startup, so it is kept rather than reported as a conflict.
                logKeptLoginView(owner, loginViewClass.getName(), loginPage);
            } else if (loginUrl != null && !loginUrl.equals(loginPage)) {
                throw new IllegalStateException(owner + " sets the login view "
                        + loginUrl + ", which conflicts with " + FORM_LOGIN_PAGE + "=" + loginPage
                        + ". The login view is taken from that property: remove the setLoginView call from the "
                        + "bean, or set the property to the same view.");
            } else {
                // A no-op if the same login view is already set.
                accessControl.setLoginView(loginPage);
            }
            return;
        }
        try {
            accessControl.setLoginView(loginPage);
        } catch (IllegalStateException alreadySet) {
            // A bean not extending QuarkusNavigationAccessControl cannot be
            // inspected, so its own login view is kept.
            logKeptLoginView(owner, alreadySet.getMessage(), loginPage);
        }
    }

    private static void logKeptLoginView(String owner, String kept, String loginPage) {
        LoggerFactory.getLogger(HillaSecurityRecorder.class)
                .info(
                        "Keeping the login view of {} ({}). {}={} is not applied to navigation within the application.",
                        owner,
                        kept,
                        FORM_LOGIN_PAGE,
                        loginPage);
    }

    /**
     * Marks the Hilla Security Policy as used in Vaadin usage statistics.
     */
    private void markSecurityPolicyUsed() {
        UsageStatistics.markAsUsed(
                "mcollovati/quarkus-hilla-security-policy",
                QuarkusHillaExtension.getVersion().orElse("-"));
    }
}
