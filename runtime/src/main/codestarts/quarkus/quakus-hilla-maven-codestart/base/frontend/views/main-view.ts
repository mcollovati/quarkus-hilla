import "@vaadin/button";

import "@vaadin/text-field";
import { HelloEndpoint } from "Frontend/generated/endpoints";

import { LitElement, html } from "lit";
import { customElement, state } from "lit/decorators.js";

@customElement("main-view")
export class MainLayout extends LitElement {
  name?: string;
  @state()
  responses: any[] = [];

  render() {
    return html`
      <div>
        <vaadin-text-field
          label="Your name"
          @input=${(e: any) => (this.name = e.target!.value)}
        ></vaadin-text-field>
        <vaadin-button @click="${(_e: MouseEvent) => this.sayHello()}"
          >Say hello</vaadin-button
        >
      </div>
      ${this.responses.map((response) => html`<div>${response}</div>`)}
    `;
  }
  async sayHello() {
    const response = await HelloEndpoint.sayHello(this.name || "");
    this.responses = [response, ...this.responses];
  }
}
