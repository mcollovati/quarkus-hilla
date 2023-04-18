package org.acme.hilla.test.extension;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.Principal;
import java.util.function.Function;

import io.quarkus.security.identity.SecurityIdentity;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.server.auth.ViewAccessChecker;

@Singleton
public class QuarkusViewAccessChecker extends ViewAccessChecker {

    private final SecurityIdentity securityIdentity;
    private final AccessAnnotationChecker annotationChecker;

    @Inject
    public QuarkusViewAccessChecker(SecurityIdentity securityIdentity,
            AccessAnnotationChecker annotationChecker) {
        this.securityIdentity = securityIdentity;
        this.annotationChecker = annotationChecker;
    }

    @Override
    protected Principal getPrincipal(VaadinRequest request) {
        if (request == null) {
            return securityIdentity.getPrincipal();
        }
        return super.getPrincipal(request);
    }

    @Override
    protected Function<String, Boolean> getRolesChecker(VaadinRequest request) {
        if (request == null) {
            return securityIdentity::hasRole;
        }
        return super.getRolesChecker(request);
    }

    @Singleton
    public static class Installer {

        private final ViewAccessChecker viewAccessChecker;

        @Inject
        public Installer(ViewAccessChecker viewAccessChecker) {
            this.viewAccessChecker = viewAccessChecker;
        }

        void installViewAccessChecker(@Observes ServiceInitEvent event) {
            event.getSource().addUIInitListener(uiInitEvent -> uiInitEvent
                    .getUI().addBeforeEnterListener(viewAccessChecker));
        }
    }
}