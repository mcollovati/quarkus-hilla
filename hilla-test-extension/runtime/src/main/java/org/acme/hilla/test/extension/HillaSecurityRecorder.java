package org.acme.hilla.test.extension;

import java.util.function.Supplier;

import io.quarkus.arc.Arc;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;

@Recorder
public class HillaSecurityRecorder {
    final HttpBuildTimeConfig buildTimeConfig;

    public HillaSecurityRecorder(HttpBuildTimeConfig buildTimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
    }

    public Supplier<HillaFormAuthenticationMechanism> setupFormAuthenticationMechanism() {
        FormAuthConfig formConfig = buildTimeConfig.auth.form;
        return () -> {
            FormAuthenticationMechanism delegate = Arc.container()
                    .instance(FormAuthenticationMechanism.class).get();
            return new HillaFormAuthenticationMechanism(delegate,
                    formConfig.cookieName, "/logout");
        };
    }

    public void configureHttpSecurityPolicy(BeanContainer container) {
        FormAuthConfig formConfig = buildTimeConfig.auth.form;
        if (formConfig.enabled) {
            HillaSecurityPolicy policy = container
                    .beanInstance(HillaSecurityPolicy.class);
            policy.withFormLogin(formConfig);
        }
    }
}
