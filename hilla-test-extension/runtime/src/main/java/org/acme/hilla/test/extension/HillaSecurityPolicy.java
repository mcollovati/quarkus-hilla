package org.acme.hilla.test.extension;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.quarkus.runtime.Startup;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.FormAuthConfig;
import io.quarkus.vertx.http.runtime.security.AuthenticatedHttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.quarkus.vertx.http.runtime.security.PathMatcher;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.flow.router.Router;
import com.vaadin.flow.router.internal.NavigationRouteTarget;
import com.vaadin.flow.router.internal.RouteTarget;
import com.vaadin.flow.server.HandlerHelper;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.AnonymousAllowed;

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
        Arrays.stream(
                HandlerHelper.getPublicResourcesRequiringSecurityContext())
                .forEach(this::addPathMatcher);
        addPathMatcher("/HILLA/**");
        addPathMatcher("/connect/**");
        Arrays.stream(HandlerHelper.getPublicResources())
                .forEach(this::addPathMatcher);
    }

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request,
            Uni<SecurityIdentity> identity,
            AuthorizationRequestContext requestContext) {
        Boolean permittedPath = pathMatcher.match(request.request().path())
                .getValue();
        if (isFrameworkInternalRequest(request) || isAnonymousRoute(request)
                || (permittedPath != null && permittedPath)) {
            return Uni.createFrom().item(CheckResult.PERMIT);
        }
        return authenticatedHttpSecurityPolicy.checkPermission(request,
                identity, requestContext);
    }

    void withFormLogin(FormAuthConfig formLogin) {
        Set<String> paths = new HashSet<>();
        UnaryOperator<String> removeQueryString = path -> path
                .replaceFirst("\\?.*", "");

        formLogin.loginPage.map(removeQueryString).ifPresent(paths::add);
        formLogin.errorPage.map(removeQueryString).ifPresent(paths::add);
        paths.add(removeQueryString.apply(formLogin.postLocation));
        paths.forEach(this::addPathMatcher);
    }

    private void addPathMatcher(String path) {
        if (path.endsWith("/") || path.endsWith("/**")) {
            pathMatcher.addPrefixPath(path.replaceFirst("/(\\*\\*)?$", ""),
                    true);
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
        return QuarkusHandlerHelper.isFrameworkInternalRequest(vaadinMapping,
                request);
    }

    /**
     * Checks whether the request targets a Flow route that is public, i.e.
     * marked as @{@link AnonymousAllowed}.
     *
     * @param request
     *            the servlet request
     * @return {@code true} if the request is targeting an anonymous route,
     *         {@code false} otherwise
     */
    public boolean isAnonymousRoute(RoutingContext request) {
        // String vaadinMapping = configurationProperties.getUrlMapping();
        String vaadinMapping = "/*";
        String requestedPath = QuarkusHandlerHelper
                .getRequestPathInsideContext(request);
        Optional<String> maybePath = HandlerHelper
                .getPathIfInsideServlet(vaadinMapping, requestedPath);
        if (!maybePath.isPresent()) {
            return false;
        }
        String path = maybePath.get();
        if (path.startsWith("/")) {
            // Requested path includes a beginning "/" but route mapping is done
            // without one
            path = path.substring(1);
        }

        if (vaadinService == null) {
            getLogger()
                    .warn("========== No VaadinService now " + requestedPath);
            return true;
        }

        Router router = vaadinService.getRouter();
        RouteRegistry routeRegistry = router.getRegistry();

        NavigationRouteTarget target = routeRegistry
                .getNavigationRouteTarget(path);
        if (target == null) {
            return false;
        }
        RouteTarget routeTarget = target.getRouteTarget();
        if (routeTarget == null) {
            return false;
        }
        Class<? extends com.vaadin.flow.component.Component> targetView = routeTarget
                .getTarget();
        if (targetView == null) {
            return false;
        }

        // Check if a not authenticated user can access the view
        boolean result = accessAnnotationChecker.hasAccess(targetView, null,
                role -> false);
        if (result) {
            getLogger().debug(path + " refers to a public view");
        }
        return result;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    void onVaadinServiceInit(@Observes ServiceInitEvent serviceInitEvent) {
        vaadinService = serviceInitEvent.getSource();
    }
}