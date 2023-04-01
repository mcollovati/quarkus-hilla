package org.acme.hilla.test.extension;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import dev.hilla.EndpointProperties;

@ApplicationScoped
public class QuarkusEndpointProperties extends EndpointProperties {

    @Inject
    QuarkusEndpointConfiguration endpointConfiguration;

    @Override
    public String getEndpointPrefix() {
        return endpointConfiguration.getEndpointPrefix();
    }

}
