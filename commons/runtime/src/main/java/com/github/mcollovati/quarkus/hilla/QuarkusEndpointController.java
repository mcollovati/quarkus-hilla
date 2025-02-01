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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.hilla.EndpointController;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;
import org.springframework.http.ResponseEntity;

import com.github.mcollovati.quarkus.hilla.multipart.MultipartRequest;

@Path("")
public class QuarkusEndpointController {

    static final String ENDPOINT_METHODS = "/{endpoint}/{method}";

    private final EndpointController delegate;

    @Inject
    public QuarkusEndpointController(EndpointController delegate) {
        this.delegate = delegate;
        QuarkusHillaExtension.markUsed();
    }

    /**
     * Captures and processes the Vaadin endpoint requests.
     * <p>
     * Matches the endpoint name and a method name with the corresponding Java
     * class and a public method in the class. Extracts parameters from a
     * request body if the Java method requires any and applies in the same
     * order. After the method call, serializes the Java method execution result
     * and sends it back.
     * <p>
     * If an issue occurs during the request processing, an error response is
     * returned instead of the serialized Java method return value.
     *
     * @param endpointName the name of an endpoint to address the calls to, not case
     *                     sensitive
     * @param methodName   the method name to execute on an endpoint, not case sensitive
     * @param body         optional request body, that should be specified if the method
     *                     called has parameters
     * @param request      the current request which triggers the endpoint call
     * @param response     the current response
     * @return execution result as a JSON string or an error message string
     */
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
        return buildResponse(endpointResponse);
    }

    /**
     * Captures and processes the Vaadin multipart endpoint requests. They are
     * used when there are uploaded files.
     * <p>
     * This method works as
     * {@link #serveEndpoint(String, String, HttpServletRequest, HttpServletResponse, ObjectNode)},
     * but it also captures the files uploaded in the request.
     *
     * @param endpointName the name of an endpoint to address the calls to, not case
     *                     sensitive
     * @param methodName   the method name to execute on an endpoint, not case sensitive
     * @param request      the current multipart request which triggers the endpoint call
     * @param response     the current response
     * @return execution result as a JSON string or an error message string
     */
    @POST
    @Path(ENDPOINT_METHODS)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response serveMultipartEndpoint(
            @PathParam("endpoint") String endpointName,
            @PathParam("method") String methodName,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response,
            MultipartFormDataInput formData)
            throws IOException {
        ResponseEntity<String> endpointResponse = delegate.serveMultipartEndpoint(
                endpointName, methodName, new MultipartRequest(request, formData), response);
        return buildResponse(endpointResponse);
    }

    private static Response buildResponse(ResponseEntity<String> endpointResponse) {
        Response.ResponseBuilder builder =
                Response.status(endpointResponse.getStatusCode().value());
        endpointResponse.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        if (endpointResponse.hasBody()) {
            builder.entity(endpointResponse.getBody());
        }
        return builder.build();
    }
}
