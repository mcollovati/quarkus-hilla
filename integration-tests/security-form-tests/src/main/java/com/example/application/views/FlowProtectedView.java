/*
 * Copyright 2025 Marco Collovati, Dario Götze
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

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle(FlowProtectedView.TITLE)
@Route("flow-protected")
@Menu(order = 5, icon = LineAwesomeIconUrl.LOCK_SOLID)
@PermitAll
public class FlowProtectedView extends AbstractFlowView {

    public static final String TITLE = "Flow - Authenticated";

    public FlowProtectedView() {
        super(TITLE, "All authenticated users can see this page");
    }
}
