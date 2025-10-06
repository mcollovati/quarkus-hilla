import { LitElement, html} from 'lit';
import './../quarkus-hilla-commons/qwc-quarkus-hilla-browser-callables.js';
import {endpoints} from 'build-time-data';

export class QwcQuarkusHilla extends LitElement {

    render() {
        return html`
            <qwc-quarkus-hilla-browser-callables
                    namespace='com.github.mcollovati.quarkus-hilla'
                    .endpoints=${endpoints}></qwc-quarkus-hilla-browser-callables>`;
    }
}
customElements.define('qwc-quarkus-hilla', QwcQuarkusHilla);
