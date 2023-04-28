package org.acme.hilla.test.extension.deployment.endpoints;

import dev.hilla.Endpoint;

import com.vaadin.flow.server.auth.AnonymousAllowed;

@Endpoint
@AnonymousAllowed
public class TestEndpoint {

    public String echo(String message) {
        return message;
    }
}
