/*
 * Copyright 2026 Marco Collovati, Dario Götze
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
package com.example.application.services;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;

import com.example.application.VaadinServiceHolder;
import com.example.application.views.FlowAdminView;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.NavigationAccessControl;
import com.vaadin.hilla.BrowserCallable;
import io.quarkus.security.identity.SecurityIdentity;

@BrowserCallable
public class BasicSecurityService {

    private final SecurityIdentity securityIdentity;
    private final NavigationAccessControl navigationAccessControl;
    private final VaadinServiceHolder vaadinServiceHolder;

    public BasicSecurityService(
            SecurityIdentity securityIdentity,
            NavigationAccessControl navigationAccessControl,
            VaadinServiceHolder vaadinServiceHolder) {
        this.securityIdentity = securityIdentity;
        this.navigationAccessControl = navigationAccessControl;
        this.vaadinServiceHolder = vaadinServiceHolder;
    }

    @AnonymousAllowed
    public String anonymous() {
        return "ANONYMOUS";
    }

    @PermitAll
    public String authenticated() {
        return "AUTHENTICATED:" + securityIdentity.getPrincipal().getName();
    }

    @RolesAllowed("USER")
    public String userOnly() {
        return "USER";
    }

    @RolesAllowed("ADMIN")
    public String adminOnly() {
        return "ADMIN";
    }

    @PermitAll
    public String adminNavigationDecision() {
        var context = navigationAccessControl.createNavigationContext(
                FlowAdminView.class, "flow-admin", vaadinServiceHolder.get(), null);
        return navigationAccessControl.checkAccess(context, false).decision().name();
    }
}
