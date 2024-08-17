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

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.hilla.Endpoint;
import com.vaadin.hilla.EndpointController;
import com.vaadin.hilla.EndpointInvoker;
import com.vaadin.hilla.EndpointRegistry;
import com.vaadin.hilla.auth.CsrfChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;

@Path("")
public class QuarkusEndpointController {

    static final String ENDPOINT_METHODS = "/{endpoint}/{method}";

    private final EndpointController delegate;

    /**
     * A constructor used to initialize the controller.
     *
     * @param context          Spring context to extract beans annotated with
     *                         {@link Endpoint} from
     * @param endpointRegistry the registry used to store endpoint information
     * @param endpointInvoker  then end point invoker
     * @param csrfChecker      the csrf checker to use
     */
    public QuarkusEndpointController(
            ApplicationContext context,
            EndpointRegistry endpointRegistry,
            EndpointInvoker endpointInvoker,
            CsrfChecker csrfChecker) {
        delegate = new EndpointController(context, endpointRegistry, endpointInvoker, csrfChecker);
    }

    @Inject
    public QuarkusEndpointController(EndpointController delegate) {
        this.delegate = delegate;
        QuarkusHillaExtension.markUsed();
    }

    @POST
    @Path(ENDPOINT_METHODS)
    @Produces(MediaType.APPLICATION_JSON)
    public Response serveEndpoint(
            @PathParam("endpoint") String endpointName,
            @PathParam("method") String methodName,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            ObjectNode body) {

        ResponseEntity<String> endpointResponse =
                delegate.serveEndpoint(endpointName, methodName, body, request, response);
        Response.ResponseBuilder builder =
                Response.status(endpointResponse.getStatusCode().value());
        endpointResponse.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (endpointResponse.hasBody()) {
            builder.entity(endpointResponse.getBody());
        }
        return builder.build();
    }
}
