package com.example.application.flow;

import com.example.application.ClockService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import reactor.core.Disposable;

@Route("flow-view")
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
        add(toggleButton, timeDiv);
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
