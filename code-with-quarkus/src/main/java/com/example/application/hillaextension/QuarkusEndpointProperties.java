package com.example.application.hillaextension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import dev.hilla.EndpointProperties;

@ApplicationScoped
public class QuarkusEndpointProperties extends EndpointProperties {

    @Inject
    EndpointConfiguration endpointConfiguration;

    @Override
    public String getEndpointPrefix() {
        return endpointConfiguration.getEndpointPrefix();
    }

}
