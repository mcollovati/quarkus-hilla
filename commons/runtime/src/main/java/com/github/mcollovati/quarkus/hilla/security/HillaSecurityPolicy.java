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
package com.github.mcollovati.quarkus.hilla.security;

import jakarta.enterprise.event.Observes;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AccessCheckDecision;
import com.vaadin.flow.server.auth.AccessCheckResult;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.flow.server.auth.NavigationContext;
import com.vaadin.hilla.parser.utils.Streams;
import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.AuthenticatedHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.ImmutablePathMatcher;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mcollovati.quarkus.hilla.QuarkusEndpointConfiguration;

@Startup
public class HillaSecurityPolicy implements HttpSecurityPolicy {

    private ImmutablePathMatcher<Boolean> permitAllMatcher;
    private final AuthenticatedHttpSecurityPolicy authenticatedHttpSecurityPolicy;

    private final NavigationAccessControl accessControl;
    private final QuarkusEndpointConfiguration endpointConfiguration;
    private final EndpointUtil endpointUtil;

    private VaadinService vaadinService;
    private RouteUtil routeUtil;
    private WebIconsRequestMatcher webIconsRequestMatcher;

    public HillaSecurityPolicy(
            NavigationAccessControl accessControl,
            QuarkusEndpointConfiguration endpointConfiguration,
            EndpointUtil endpointUtil) {
        this.authenticatedHttpSecurityPolicy = new AuthenticatedHttpSecurityPolicy();
        this.accessControl = accessControl;
        this.endpointConfiguration = endpointConfiguration;
        this.endpointUtil = endpointUtil;
        buildPathMatcher(null);
    }

    private void buildPathMatcher(Consumer<ImmutablePathMatcher.ImmutablePathMatcherBuilder<Boolean>> customizer) {
        ImmutablePathMatcher.ImmutablePathMatcherBuilder<Boolean> pathMatcherBuilder = ImmutablePathMatcher.builder();
        String connectPath = endpointConfiguration.getNormalizedEndpointPrefix();
        pathMatcherBuilder.addPath(connectPath + "/*", true);
        pathMatcherBuilder.addPath("/HILLA/*", true);
        Streams.combine(
                        HandlerHelper.getPublicResources(),
                        HandlerHelper.getPublicResourcesRoot(),
                        // Contains /VAADIN/*
                        HandlerHelper.getPublicResourcesRequiringSecurityContext())
                .map(PathUtil::normalizeWildcard)
                .forEach(p -> pathMatcherBuilder.addPath(p, true));
        if (customizer != null) {
            customizer.accept(pathMatcherBuilder);
        }
        this.permitAllMatcher = pathMatcherBuilder.build();
    }

    @Override
    public Uni<CheckResult> checkPermission(
            RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext) {
        Boolean permittedPath = permitAllMatcher.match(request.request().path()).getValue();
        if ((permittedPath != null && permittedPath)
                || isFrameworkInternalRequest(request)
                || isAnonymousEndpoint(request)
                || isAnonymousRoute(tryCreateNavigationContext(request), request.normalizedPath())
                || isCustomWebIcon(request)) {
            return CheckResult.permit();
        }
        return identity.flatMap(secIdentity -> {
            if (isAllowedHillaView(request, secIdentity)) return CheckResult.permit();
            return authenticatedHttpSecurityPolicy.checkPermission(request, identity, requestContext);
        });
    }

    private boolean isAllowedHillaView(RoutingContext request, SecurityIdentity secIdentity) {
        return routeUtil.isRouteAllowed(request, secIdentity);
    }

    private boolean isCustomWebIcon(RoutingContext request) {
        return webIconsRequestMatcher.isWebIconRequest(request.request().path());
    }

    private boolean isAnonymousEndpoint(RoutingContext request) {
        return endpointUtil.isAnonymousEndpoint(request);
    }

    void withFormLogin(Config config) {
        Set<String> paths = new HashSet<>();
        UnaryOperator<String> removeQueryString = path -> path.replaceFirst("\\?.*", "");

        config.getOptionalValue("quarkus.http.auth.form.login-page", String.class)
                .map(removeQueryString)
                .ifPresent(paths::add);
        config.getOptionalValue("quarkus.http.auth.form.error-page", String.class)
                .map(removeQueryString)
                .ifPresent(paths::add);
        paths.add(removeQueryString.apply(config.getValue("quarkus.http.auth.form.post-location", String.class)));
        buildPathMatcher(builder -> paths.forEach(p -> builder.addPath(PathUtil.normalizeWildcard(p), true)));
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
        String vaadinMapping = getUrlMapping();
        return QuarkusHandlerHelper.isFrameworkInternalRequest(vaadinMapping, request);
    }

    private boolean isAnonymousRoute(NavigationContext navigationContext, String path) {

        if (vaadinService == null) {
            getLogger().warn("VaadinService not set. Cannot determine server route for {}", path);
            return true;
        }
        if (navigationContext == null) {
            getLogger().trace("No route defined for {}", path);
            return true;
        }
        boolean productionMode = vaadinService.getDeploymentConfiguration().isProductionMode();

        if (!accessControl.isEnabled()) {
            String message =
                    "Navigation Access Control is disabled. Cannot determine if {} refers to a public view, thus access is denied. Please add an explicit request matcher rule for this URL.";
            if (productionMode) {
                getLogger().debug(message, path);
            } else {
                getLogger().info(message, path);
            }
            return true;
        }

        AccessCheckResult result = accessControl.checkAccess(navigationContext, productionMode);
        boolean isAllowed = result.decision() == AccessCheckDecision.ALLOW;
        if (isAllowed) {
            getLogger().debug("{} refers to a public view", path);
        } else {
            getLogger().debug("Access to {} denied by Flow navigation access control. {}", path, result.reason());
        }
        return isAllowed;
    }

    private NavigationContext tryCreateNavigationContext(RoutingContext request) {

        String vaadinMapping = getUrlMapping();
        String requestedPath = QuarkusHandlerHelper.getRequestPathInsideContext(request);
        if (vaadinService == null) {
            return null;
        }

        Router router = vaadinService.getRouter();
        RouteRegistry routeRegistry = router.getRegistry();

        NavigationRouteTarget target = HandlerHelper.getPathIfInsideServlet(vaadinMapping, requestedPath)
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
                .orElse(null);
        if (target == null) {
            return null;
        }
        RouteTarget routeTarget = target.getRouteTarget();
        if (routeTarget == null) {
            return null;
        }
        Class<? extends com.vaadin.flow.component.Component> targetView = routeTarget.getTarget();
        if (targetView == null) {
            return null;
        }

        return new NavigationContext(
                router,
                targetView,
                new Location(requestedPath, queryParametersFromRequest(request)),
                target.getRouteParameters(),
                null,
                role -> false,
                false);
    }

    private QueryParameters queryParametersFromRequest(RoutingContext routingContext) {
        MultiMap params = routingContext.request().params();
        return QueryParameters.full(params.names().stream()
                .map(name -> Map.entry(name, params.getAll(name).toArray(String[]::new)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private String getUrlMapping() {
        return "/*";
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    void onVaadinServiceInit(@Observes ServiceInitEvent serviceInitEvent) {
        vaadinService = serviceInitEvent.getSource();
        routeUtil = new RouteUtil(vaadinService);
        webIconsRequestMatcher = new WebIconsRequestMatcher(vaadinService, getUrlMapping());
    }
}
