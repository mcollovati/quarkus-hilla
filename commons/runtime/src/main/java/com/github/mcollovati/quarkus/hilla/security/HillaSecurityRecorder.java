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
import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import com.github.mcollovati.quarkus.hilla.QuarkusHillaExtension;

@Recorder
public class HillaSecurityRecorder {

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        String cookieName = ConfigProvider.getConfig().getValue("quarkus.http.auth.form.cookie-name", String.class);
        String landingPage = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.auth.form.landing-page", String.class)
                .orElse("/");
        return () -> {
            FormAuthenticationMechanism delegate =
                    Arc.container().instance(FormAuthenticationMechanism.class).get();
            return new HillaFormAuthenticationMechanism(delegate, cookieName, landingPage, "/logout");
        };
    }

    public void configureFormLoginHttpSecurityPolicy(BeanContainer container) {
        Config config = ConfigProvider.getConfig();
        HillaSecurityPolicy policy = container.beanInstance(HillaSecurityPolicy.class);
        policy.withFormLogin(config);
    }

    public void configureNavigationAccessControl(
            BeanContainer container, String loginPath, boolean restorePathAfterLogin) {
        QuarkusNavigationAccessControl accessChecker = container.beanInstance(QuarkusNavigationAccessControl.class);
        accessChecker.setLoginView(loginPath);
        accessChecker.setRestorePathAfterLogin(restorePathAfterLogin);
    }

    /**
     * Marks the Hilla Security Policy as used in Vaadin usage statistics.
     */
    public void markSecurityPolicyUsed() {
        UsageStatistics.markAsUsed(
                "mcollovati/quarkus-hilla-security-policy",
                QuarkusHillaExtension.getVersion().orElse("-"));
    }
}
