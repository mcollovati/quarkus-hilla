import '@vaadin/button';
import '@vaadin/notification';
import {Notification} from '@vaadin/notification';
import '@vaadin/text-field';
import UserPOJO from 'Frontend/generated/com/example/application/entities/UserPOJO';
import * as HelloWorldEndpoint from 'Frontend/generated/HelloWorldEndpoint';
import {html} from 'lit';
import {customElement} from 'lit/decorators.js';
import {View} from '../../views/view';

@customElement('hello-world-view')
export class HelloWorldView extends View {
    name = '';

    connectedCallback() {
        super.connectedCallback();
        this.classList.add('flex', 'p-m', 'gap-m', 'items-end');
    }

    render() {
        return html`
            <vaadin-text-field label="Your name" @value-changed=${this.nameChanged}></vaadin-text-field>
            <vaadin-button @click=${this.sayHello}>Say hello</vaadin-button>
            <vaadin-button @click=${this.sayComplexHello}>Say complex hello</vaadin-button>
            <vaadin-button @click=${this.testme}>Test me</vaadin-button>
        `;
    }

    nameChanged(e: CustomEvent) {
        this.name = e.detail.value;
    }

    async sayHello() {
        const serverResponse = await HelloWorldEndpoint.sayHello(this.name);
        Notification.show(serverResponse);
    }

    async sayComplexHello() {
        const pojo: UserPOJO = {
            name: this.name,
            surname: "surname"
        }
        const serverResponse = await HelloWorldEndpoint.sayComplexHello(pojo);
        Notification.show(serverResponse);
    }
    async testme() {
      console.log("============================ CC????")
      HelloWorldEndpoint.getClock()
          .onNext((msg) => console.log("NEXT " + msg))
          .onError(() => console.log("ERROR"))
          .onComplete(() => console.log("COMPLETE"));
    }

}
