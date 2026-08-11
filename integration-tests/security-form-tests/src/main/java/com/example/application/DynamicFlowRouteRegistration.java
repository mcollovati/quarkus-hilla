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
package com.example.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import com.example.application.views.DynamicFlowAdminView;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.ServiceInitEvent;

@ApplicationScoped
class DynamicFlowRouteRegistration {

    void register(@Observes ServiceInitEvent ignored) {
        RouteConfiguration routes = RouteConfiguration.forApplicationScope();
        if (!routes.isRouteRegistered(DynamicFlowAdminView.class)) {
            routes.setAnnotatedRoute(DynamicFlowAdminView.class);
        }
    }
}
