package com.github.mcollovati.quarkus.hilla;

import dev.hilla.EndpointProperties;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class QuarkusEndpointProperties extends EndpointProperties {

    @Inject
    QuarkusEndpointConfiguration endpointConfiguration;

    @Override
    public String getEndpointPrefix() {
        return endpointConfiguration.getEndpointPrefix();
    }
}
