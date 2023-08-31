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

import jakarta.annotation.security.RolesAllowed;

import com.example.application.ClockService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import reactor.core.Disposable;

@Route("flow-view")
@RolesAllowed({"ADMIN", "ROLE_ADMIN"})
public class FlowView extends VerticalLayout {

    private final ClockService clockService;
    private Disposable runningClock;
    private final Div timeDiv = new Div();

    public FlowView(ClockService clockService) {
        this.clockService = clockService;
        buildUI();
    }

    private void buildUI() {

        var toggleButton = new Button("Toggle Clock");
        toggleButton.addClickListener(event -> toggleClock());
        Span title = new Span("Flow view");
        title.setId("title");
        timeDiv.setId("time");
        add(title, toggleButton, timeDiv);
    }

    private void toggleClock() {
        if (runningClock == null) {
            runningClock = clockService.getClock().subscribe(this::updateDiv);
        } else {
            runningClock.dispose();
            runningClock = null;
            updateDiv("Stopped");
        }
    }

    private void updateDiv(String text) {
        getUI().ifPresent(ui -> ui.access(() -> {
            System.out.println("Updating div with " + text);
            timeDiv.setText(text);
        }));
    }
}
