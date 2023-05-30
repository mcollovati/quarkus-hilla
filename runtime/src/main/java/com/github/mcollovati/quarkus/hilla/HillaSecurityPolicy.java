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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.security.AuthenticatedHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.PathMatcher;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Startup
public class HillaSecurityPolicy implements HttpSecurityPolicy {

    private final PathMatcher<Boolean> pathMatcher;
    private final AuthenticatedHttpSecurityPolicy authenticatedHttpSecurityPolicy;

    @Inject
    AccessAnnotationChecker accessAnnotationChecker;

    VaadinService vaadinService;

    public HillaSecurityPolicy() {
        authenticatedHttpSecurityPolicy = new AuthenticatedHttpSecurityPolicy();
        pathMatcher = new PathMatcher<>();
        Arrays.stream(HandlerHelper.getPublicResourcesRequiringSecurityContext())
                .forEach(this::addPathMatcher);
        addPathMatcher("/HILLA/**");
        addPathMatcher("/connect/**");
        Arrays.stream(HandlerHelper.getPublicResources()).forEach(this::addPathMatcher);
    }

    @Override
    public Uni<CheckResult> checkPermission(
            RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext) {
        Boolean permittedPath = pathMatcher.match(request.request().path()).getValue();
        Class<? extends Component> maybeRoot = detectRoute(request);
        if ((permittedPath != null && permittedPath)
                || isFrameworkInternalRequest(request)
                || isAnonymousRoute(maybeRoot, request.normalizedPath())) {
            return Uni.createFrom().item(CheckResult.PERMIT);
        }
        return authenticatedHttpSecurityPolicy.checkPermission(request, identity, requestContext);
    }

    void withFormLogin(FormAuthConfig formLogin) {
        Set<String> paths = new HashSet<>();
        UnaryOperator<String> removeQueryString = path -> path.replaceFirst("\\?.*", "");

        formLogin.loginPage.map(removeQueryString).ifPresent(paths::add);
        formLogin.errorPage.map(removeQueryString).ifPresent(paths::add);
        paths.add(removeQueryString.apply(formLogin.postLocation));
        paths.forEach(this::addPathMatcher);
    }

    private void addPathMatcher(String path) {
        if (path.endsWith("/") || path.endsWith("/**")) {
            pathMatcher.addPrefixPath(path.replaceFirst("/(\\*\\*)?$", ""), true);
        } else {
            pathMatcher.addExactPath(path, true);
        }
    }

    /**
     * Checks whether the request is an internal request.
     *
     * An internal request is one that is needed for all Vaadin applications to
     * function, e.g. UIDL or init requests.
     *
     * Note that bootstrap requests for any route or static resource requests
     * are not internal, neither are resource requests for the JS bundle.
     *
     * @param request
     *            the servlet request
     * @return {@code true} if the request is Vaadin internal, {@code false}
     *         otherwise
     */
    public boolean isFrameworkInternalRequest(RoutingContext request) {
        // String vaadinMapping = configurationProperties.getUrlMapping();
        String vaadinMapping = "/*";
        return QuarkusHandlerHelper.isFrameworkInternalRequest(vaadinMapping, request);
    }

    private boolean isAnonymousRoute(Class<? extends Component> routeClass, String path) {

        if (vaadinService == null) {
            getLogger().warn("VaadinService not set. Cannot determine server route for {}", path);
            return true;
        }
        if (routeClass == null) {
            getLogger().trace("No route defined for {}", path);
            return true;
        }

        boolean result = accessAnnotationChecker.hasAccess(routeClass, null, role -> false);
        if (result) {
            getLogger().debug("{} refers to a public view", path);
        }
        return result;
    }

    private Class<? extends Component> detectRoute(RoutingContext request) {

        String vaadinMapping = "/*";
        String requestedPath = QuarkusHandlerHelper.getRequestPathInsideContext(request);
        if (vaadinService == null) {
            return null;
        }

        Router router = vaadinService.getRouter();
        RouteRegistry routeRegistry = router.getRegistry();

        return HandlerHelper.getPathIfInsideServlet(vaadinMapping, requestedPath)
                .map(path -> {
                    if (path.startsWith("/")) {
                        // Requested path includes a beginning "/" but route
                        // mapping is done
                        // without one
                        path = path.substring(1);
                    }
                    return path;
                })
                .map(routeRegistry::getNavigationRouteTarget)
                .map(NavigationRouteTarget::getRouteTarget)
                .map(RouteTarget::getTarget)
                .orElse(null);
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    void onVaadinServiceInit(@Observes ServiceInitEvent serviceInitEvent) {
        vaadinService = serviceInitEvent.getSource();
    }
}
