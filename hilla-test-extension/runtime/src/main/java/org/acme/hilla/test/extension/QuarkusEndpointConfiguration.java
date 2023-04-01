package org.acme.hilla.test.extension;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.ConvertWith;
import io.quarkus.runtime.configuration.NormalizeRootHttpPathConverter;
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
