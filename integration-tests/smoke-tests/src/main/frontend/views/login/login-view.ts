import { html } from 'lit';
import { customElement, state } from 'lit/decorators.js';
import { View } from 'Frontend/views/view';
import { LoginFormLoginEvent } from '@vaadin/login/vaadin-login-form.js';
import '@vaadin/login/vaadin-login-form.js';

@customElement('login-view')
export class LoginView extends View {
    @state()
    error = false;

    connectedCallback() {
        super.connectedCallback();
        const urlParams = new URLSearchParams(window.location.search);
        this.error = urlParams.has('error');

        this.classList.add('flex', 'flex-col', 'items-center', 'justify-center');
    }

    render() {
        return html`
      <h1>Hilla CRM</h1>
      <vaadin-login-form
        no-forgot-password
        action="/login"
        .error=${this.error}
      ></vaadin-login-form>
    `;
    }

    async login(e: LoginFormLoginEvent) {
        try {
            //await uiStore.login(e.detail.username, e.detail.password);
        } catch (err) {
            this.error = true;
        }
    }
}
