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

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.hilla.EndpointController;
import dev.hilla.EndpointInvoker;
import dev.hilla.EndpointRegistry;
import dev.hilla.auth.CsrfChecker;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
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
    // @Inject
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
    }

    @POST
    @Path(ENDPOINT_METHODS)
    @Produces(MediaType.APPLICATION_JSON)
    public Response serveEndpoint(
            @PathParam("endpoint") String endpointName,
            @PathParam("method") String methodName,
            @Context HttpServletRequest request,
            ObjectNode body) {

        ResponseEntity<String> response = delegate.serveEndpoint(endpointName, methodName, body, request);
        Response.ResponseBuilder builder = Response.status(response.getStatusCodeValue());
        response.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (response.hasBody()) {
            builder.entity(response.getBody());
        }
        return builder.build();
    }
}
