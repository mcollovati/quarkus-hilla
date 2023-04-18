package org.acme.hilla.test.extension;

import java.io.Serializable;
import java.util.Optional;

import io.vertx.ext.web.RoutingContext;

import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.communication.StreamRequestHandler;
import com.vaadin.flow.shared.ApplicationConstants;

import static com.vaadin.flow.server.HandlerHelper.getPathIfInsideServlet;

public class QuarkusHandlerHelper implements Serializable {

    /**
     * Checks whether the request is an internal request.
     *
     * The requests listed in {@link HandlerHelper.RequestType} are considered
     * internal as they are needed for applications to work.
     * <p>
     * Requests for routes, static resources requests and similar are not
     * considered internal requests.
     *
     * @param servletMappingPath
     *            the path the Vaadin servlet is mapped to, with or without and
     *            ending "/*"
     * @param request
     *            the servlet request
     * @return {@code true} if the request is Vaadin internal, {@code false}
     *         otherwise
     */
    public static boolean isFrameworkInternalRequest(String servletMappingPath,
            RoutingContext request) {
        return isFrameworkInternalRequest(servletMappingPath,
                getRequestPathInsideContext(request), request.request()
                        .getParam(ApplicationConstants.REQUEST_TYPE_PARAMETER));
    }

    private static boolean isFrameworkInternalRequest(String servletMappingPath,
            String requestedPath, String requestTypeParameter) {
        /*
         * According to the spec, pathInfo should be null but not all servers
         * implement it like that...
         *
         * Additionally the spring servlet is mapped as /vaadinServlet right now
         * it seems but requests are sent to /vaadinServlet/, causing a "/" path
         * info
         */

        // This is only an internal request if it is for the Vaadin servlet
        Optional<String> requestedPathWithoutServletMapping = getPathIfInsideServlet(
                servletMappingPath, requestedPath);
        if (!requestedPathWithoutServletMapping.isPresent()) {
            return false;
        } else if (isInternalRequestInsideServlet(
                requestedPathWithoutServletMapping.get(),
                requestTypeParameter)) {
            return true;
        } else if (isUploadRequest(requestedPathWithoutServletMapping.get())) {
            return true;
        }

        return false;
    }

    /**
     * Returns the requested path inside the context root.
     *
     * @param request
     *            the servlet request
     * @return the path inside the context root, not including the slash after
     *         the context root path
     */
    public static String getRequestPathInsideContext(RoutingContext request) {
        String servletPath = request.mountPoint();
        String pathInfo = request.request().path();
        String url = "";
        if (servletPath != null) {
            if (servletPath.startsWith("/")) {
                // This SHOULD always be true...
                url += servletPath.substring(1);
            } else {
                url += servletPath;
            }
        }
        if (pathInfo != null) {
            url += pathInfo;
        }
        return url;
    }

    static boolean isInternalRequestInsideServlet(
            String requestedPathWithoutServletMapping,
            String requestTypeParameter) {
        if (requestedPathWithoutServletMapping == null
                || requestedPathWithoutServletMapping.isEmpty()
                || "/".equals(requestedPathWithoutServletMapping)) {
            return requestTypeParameter != null;
        }
        return false;
    }

    private static boolean isUploadRequest(
            String requestedPathWithoutServletMapping) {
        // First key is uiId
        // Second key is security key
        return requestedPathWithoutServletMapping
                .matches(StreamRequestHandler.DYN_RES_PREFIX
                        + "(\\d+)/([0-9a-z-]*)/upload");
    }
}