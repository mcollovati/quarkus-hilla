import '@vaadin/button';
import '@vaadin/notification';
import '@vaadin/text-field';
import * as HelloWorldEndpoint from 'Frontend/generated/HelloWorldEndpoint';
import {html, nothing} from 'lit';
import {customElement, state} from 'lit/decorators.js';
import {View} from '../../views/view';
import {Subscription} from "@hilla/frontend";
import {
    getClock,
    getClockCancellable,
    getPublicClock
} from "Frontend/generated/HelloWorldEndpoint";

@customElement('push-view')
export class PushView extends View {
    name = '';

    @state()
    currentTime = '';

    @state()
    protected clockSubscription?: Subscription<string>;

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
                <vaadin-button id="public"
                               ?disabled="${!!this.clockSubscription}"
                               @click=${this.publicClock}>
                    Public
                </vaadin-button>
                <vaadin-button id="public-limit"
                               ?disabled="${!!this.clockSubscription}"
                               @click=${this.publicClockWithLimit}>
                    Public (Limit 10)
                </vaadin-button>
                <vaadin-button id="protected"
                               ?disabled="${!!this.clockSubscription}"
                               @click=${this.protectedClock}>
                    Protected
                </vaadin-button>
                <vaadin-button id="subscription"
                               ?disabled="${this.clockSubscription}"
                               @click=${this.subscriptionClock}>
                    EndpointSubscription
                </vaadin-button>
                <vaadin-button id="stop"
                               ?disabled="${!this.clockSubscription}"
                               @click=${this.stopClock}>
                    Stop
                </vaadin-button>

            </div>
            ${this.renderClock()}
        `;
    }

    private renderClock() {
        return this.currentTime ?
            html`
                <div id="push-contents">${this.currentTime}</div>` :
            nothing;
    }

    async publicClock() {
        await this.toggleClock(() => HelloWorldEndpoint.getPublicClock(undefined))
    }

    async publicClockWithLimit() {
        await this.toggleClock(() => HelloWorldEndpoint.getPublicClock(10))
    }

    async protectedClock() {
        await this.toggleClock(HelloWorldEndpoint.getClock)
    }

    async subscriptionClock() {
        await this.toggleClock(HelloWorldEndpoint.getClockCancellable)
    }

    async stopClock() {
        await this.disconnectClock();
        this.currentTime = '';
    }
    async toggleClock(endpointFactory: () => Subscription<string>) {
        await this.disconnectClock();
        this.currentTime = 'Loading...';
        this.clockSubscription = endpointFactory()
            .onNext((msg) => this.currentTime = msg)
            .onError(() => {
                this.currentTime = "Something failed. Maybe you are not authorized?";
                this.disconnectClock();
            })
            .onComplete(() => {
                this.currentTime = "Bye. Thanks.";
                this.disconnectClock();
            });
    }

    private async disconnectClock() {
        if (this.clockSubscription) {
            this.clockSubscription.cancel();
            this.clockSubscription = undefined;
        }
    }
}
