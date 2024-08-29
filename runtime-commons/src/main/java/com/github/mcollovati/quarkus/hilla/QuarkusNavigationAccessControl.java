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

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.security.Principal;
import java.util.List;
import java.util.function.Predicate;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.auth.AccessCheckDecisionResolver;
import com.vaadin.flow.server.auth.NavigationAccessChecker;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;
import io.quarkus.security.identity.SecurityIdentity;

@Singleton
@DefaultBean
public class QuarkusNavigationAccessControl extends NavigationAccessControl {

    private final SecurityIdentity securityIdentity;

    public QuarkusNavigationAccessControl(
            @All List<NavigationAccessChecker> checkerList,
            AccessCheckDecisionResolver decisionResolver,
            SecurityIdentity securityIdentity) {
        super(checkerList, decisionResolver);
        this.securityIdentity = securityIdentity;
    }

    @Override
    protected Principal getPrincipal(VaadinRequest request) {
        if (request == null) {
            return securityIdentity.getPrincipal();
        }
        return super.getPrincipal(request);
    }

    @Override
    protected Predicate<String> getRolesChecker(VaadinRequest request) {
        if (request == null) {
            return securityIdentity::hasRole;
        }
        return super.getRolesChecker(request);
    }

    @Singleton
    public static class Installer {

        private final NavigationAccessControl accessControl;

        @Inject
        public Installer(NavigationAccessControl accessControl) {
            this.accessControl = accessControl;
        }

        void installViewAccessChecker(@Observes ServiceInitEvent event) {
            event.getSource()
                    .addUIInitListener(uiInitEvent -> uiInitEvent.getUI().addBeforeEnterListener(accessControl));
        }
    }
}
