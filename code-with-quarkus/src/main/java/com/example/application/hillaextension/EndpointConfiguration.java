package com.example.application.hillaextension;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "vaadin.endpoint")
public interface EndpointConfiguration {

    @WithName("prefix")
    @WithDefault("/connect")
    String getEndpointPrefix();
}
