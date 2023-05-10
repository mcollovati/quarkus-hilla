package com.github.mcollovati.quarkus.hilla;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * hilla conf
 */
@ConfigMapping(prefix = "vaadin.endpoint")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface QuarkusEndpointConfiguration {


    /**
     * prefix
     */
    @WithName("prefix")
    @WithDefault("/connect")
    String getEndpointPrefix();


}
