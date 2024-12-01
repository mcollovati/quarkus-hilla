import { LitElement, html} from 'lit';
import './../com.github.mcollovati.quarkus-hilla-commons/qwc-quarkus-hilla-browser-callables.js';

export class QwcQuarkusHilla extends LitElement {

    render() {
        return html`
            <qwc-quarkus-hilla-browser-callables
                    namespace='com.github.mcollovati.quarkus-hilla'></qwc-quarkus-hilla-browser-callables>`;
    }
}
customElements.define('qwc-quarkus-hilla', QwcQuarkusHilla);
