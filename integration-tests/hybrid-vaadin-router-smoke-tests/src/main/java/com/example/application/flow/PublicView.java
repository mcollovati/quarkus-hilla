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
package com.example.application.flow;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("flow-public-view")
@AnonymousAllowed
public class PublicView extends Div {

    public PublicView() {
        setId("public-view");
        setText("Public view");
        RouterLink protectedViewLink = new RouterLink("Go To Protected View", ProtectedView.class);
        protectedViewLink.setId("protected-link");
        RouterLink adminViewLink = new RouterLink("Go to Admin View", AdminView.class);
        adminViewLink.setId("admin-link");
        add(protectedViewLink, adminViewLink);
    }
}
