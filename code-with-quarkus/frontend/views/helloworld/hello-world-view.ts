import '@vaadin/button';
import '@vaadin/notification';
import {Notification} from '@vaadin/notification';
import '@vaadin/text-field';
import UserPOJO from 'Frontend/generated/com/example/application/entities/UserPOJO';
import * as HelloWorldEndpoint from 'Frontend/generated/HelloWorldEndpoint';
import {html, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {View} from '../../views/view';
import {Subscription} from "@hilla/frontend";

@customElement('hello-world-view')
export class HelloWorldView extends View {
    name = '';

    @state()
    currentTime = '';

    clockSubscription?: Subscription<string>;

    connectedCallback() {
        super.connectedCallback();
        this.classList.add('flex', 'flex-col', 'p-m', 'gap-m', 'items-center');
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        this.disconnectClock();
    }


    render() {
        return html`
            <div>
                <vaadin-text-field label="Your name" @value-changed=${this.nameChanged}></vaadin-text-field>
                <vaadin-button @click=${this.sayHello}>Say hello</vaadin-button>
                <vaadin-button @click=${this.sayComplexHello}>Say complex hello</vaadin-button>
                <vaadin-button @click=${this.toggleClock}>Toggle clock</vaadin-button>
            </div>
            ${this.renderClock()}
        `;
    }

    private renderClock() {
        return this.currentTime ?
            html`<div>${this.currentTime}</div>` :
            nothing;
    }

    nameChanged(e: CustomEvent) {
        this.name = e.detail.value;
    }

    async sayHello() {
        const serverResponse = await HelloWorldEndpoint.sayHello2(this.name);
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
    async toggleClock() {
        if (this.clockSubscription) {
            this.disconnectClock();
            this.currentTime = '';
        } else {
            this.currentTime = 'Loading...';
            this.clockSubscription = HelloWorldEndpoint.getClock()
                .onNext((msg) => this.currentTime = msg)
                .onError(() => console.log("ERROR"))
                .onComplete(() => console.log("COMPLETE"));
        }
    }

    private disconnectClock() {
        if (this.clockSubscription) {
            this.clockSubscription.cancel();
            this.clockSubscription = undefined;
        }
    }
}
