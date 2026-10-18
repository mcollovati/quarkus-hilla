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
import io.quarkus.vertx.http.runtime.VertxHttpConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import io.smallrye.config.SmallRyeConfig;
import io.vertx.core.http.CookieSameSite;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import com.github.mcollovati.quarkus.hilla.QuarkusHillaExtension;

@Recorder
public class HillaSecurityRecorder {

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        return () -> {
            Config config = ConfigProvider.getConfig();
            VaadinSecurityConfig securityConfig =
                    config.unwrap(SmallRyeConfig.class).getConfigMapping(VaadinSecurityConfig.class);
            var formConfig = config.unwrap(SmallRyeConfig.class)
                    .getConfigMapping(VertxHttpConfig.class)
                    .auth()
                    .form();
            var authConfig = new HillaFormAuthenticationMechanism.Config(
                    formConfig.cookieName(),
                    formConfig.landingPage().orElse("/"),
                    securityConfig.logoutPath(),
                    securityConfig.postLogoutRedirectUri().orElse(null),
                    securityConfig.logoutInvalidateSession(),
                    formConfig.cookiePath().orElse("/"),
                    formConfig.cookieDomain().orElse(null),
                    formConfig.httpOnlyCookie(),
                    CookieSameSite.valueOf(formConfig.cookieSameSite().name()));
            FormAuthenticationMechanism delegate =
                    Arc.container().instance(FormAuthenticationMechanism.class).get();
            return new HillaFormAuthenticationMechanism(delegate, authConfig);
        };
    }

    public void configureHttpSecurityPolicy(
            BeanContainer container, boolean fileRoutesManifestExpected, boolean formAuthentication) {
        HillaSecurityPolicy policy = container.beanInstance(HillaSecurityPolicy.class);
        if (formAuthentication) {
            policy.withFormLogin(ConfigProvider.getConfig());
        }
        policy.setFileRoutesManifestExpected(fileRoutesManifestExpected);
        markSecurityPolicyUsed();
    }

    public void configureNavigationAccessControl(BeanContainer container, String loginPath) {
        QuarkusNavigationAccessControl accessChecker = container.beanInstance(QuarkusNavigationAccessControl.class);
        accessChecker.setLoginView(loginPath);
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
