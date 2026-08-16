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

import java.util.function.Supplier;

import com.vaadin.flow.internal.UsageStatistics;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.LoggerFactory;

import com.github.mcollovati.quarkus.hilla.QuarkusHillaExtension;

@Recorder
public class HillaSecurityRecorder {

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        return () -> {
            Config config = ConfigProvider.getConfig();
            VaadinSecurityConfig securityConfig =
                    config.unwrap(SmallRyeConfig.class).getConfigMapping(VaadinSecurityConfig.class);
            var authConfig = new HillaFormAuthenticationMechanism.Config(
                    config.getValue("quarkus.http.auth.form.cookie-name", String.class),
                    config.getOptionalValue("quarkus.http.auth.form.landing-page", String.class)
                            .orElse("/"),
                    securityConfig.logoutPath(),
                    securityConfig.postLogoutRedirectUri().orElse(null),
                    securityConfig.logoutInvalidateSession());
            FormAuthenticationMechanism delegate =
                    Arc.container().instance(FormAuthenticationMechanism.class).get();
            return new HillaFormAuthenticationMechanism(delegate, authConfig);
        };
    }

    public void configureHttpSecurityPolicy(BeanContainer container) {
        Config config = ConfigProvider.getConfig();
        HillaSecurityPolicy policy = container.beanInstance(HillaSecurityPolicy.class);
        policy.withFormLogin(config);
        markSecurityPolicyUsed();
    }

    public void configureNavigationAccessControl(BeanContainer container, String loginPath, boolean enabled) {
        // Resolved by base type, an application may provide its own bean.
        NavigationAccessControl accessControl = container.beanInstance(NavigationAccessControl.class);
        if (loginPath != null) {
            // The login view can be set only once and an application bean is
            // free to set its own, so only ours is configured from the Quarkus
            // form login settings.
            if (accessControl instanceof QuarkusNavigationAccessControl) {
                accessControl.setLoginView(loginPath);
            } else {
                LoggerFactory.getLogger(HillaSecurityRecorder.class)
                        .debug(
                                "quarkus.http.auth.form.login-page is not applied to {}, an application provided "
                                        + "navigation access control configures its login view itself.",
                                accessControl.getClass().getName());
            }
        }
        if (!enabled) {
            // Never turns the access control on, an application disabling its
            // own bean keeps that state.
            accessControl.setEnabled(false);
            LoggerFactory.getLogger(HillaSecurityRecorder.class)
                    .warn("Flow view access checking is turned off by "
                            + "vaadin.security.navigation-access-control.enabled=false. Every Flow view is "
                            + "reachable regardless of its access annotations.");
        }
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
