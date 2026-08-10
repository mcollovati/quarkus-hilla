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

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@PageTitle("Flow - Bearer Only")
@Route("flow-bearer-only")
@PermitAll
public class FlowBearerOnlyView extends AbstractFlowView {

    public FlowBearerOnlyView() {
        super("flow-bearer-only", "Flow - Bearer Only", "This route requires the OIDC bearer mechanism");
    }
}
