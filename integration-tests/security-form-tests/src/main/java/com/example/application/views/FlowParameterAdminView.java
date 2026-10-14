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
package com.example.application.views;

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.router.Route;

/**
 * Admin-only Flow test route with a dynamic path parameter.
 */
@Route("flow-parameter-admin/:id")
@RolesAllowed("ADMIN")
public class FlowParameterAdminView extends AbstractFlowView {

    public FlowParameterAdminView() {
        super("Flow - Parameter Admin", "Only users with role ADMIN see this page");
    }
}
