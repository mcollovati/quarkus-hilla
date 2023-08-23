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
package com.github.mcollovati.quarkus.hilla;

import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import java.util.function.Supplier;

@Recorder
public class HillaSecurityRecorder {
    final HttpBuildTimeConfig buildTimeConfig;

    public HillaSecurityRecorder(HttpBuildTimeConfig buildTimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
    }

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        FormAuthConfig formConfig = buildTimeConfig.auth.form;
        return () -> {
            FormAuthenticationMechanism delegate =
                    Arc.container().instance(FormAuthenticationMechanism.class).get();
            return new HillaFormAuthenticationMechanism(delegate, formConfig.cookieName, "/logout");
        };
    }

    public void configureHttpSecurityPolicy(BeanContainer container) {
        FormAuthConfig formConfig = buildTimeConfig.auth.form;
        if (formConfig.enabled) {
            HillaSecurityPolicy policy = container.beanInstance(HillaSecurityPolicy.class);
            policy.withFormLogin(formConfig);
            QuarkusHillaExtension.markSecurityPolicyUsed();
        }
    }

    public void configureFlowViewAccessChecker(BeanContainer container, String loginPath) {
        QuarkusViewAccessChecker accessChecker = container.beanInstance(QuarkusViewAccessChecker.class);
        accessChecker.setLoginView(loginPath);
        accessChecker.enable();
    }
}
