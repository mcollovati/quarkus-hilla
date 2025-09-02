import { LitElement, html} from 'lit';
import './../quarkus-hilla-commons/qwc-quarkus-hilla-browser-callables.js';

export class QwcQuarkusHillaReact extends LitElement {

    render() {
        return html`
            <qwc-quarkus-hilla-browser-callables
                    namespace='com.github.mcollovati.quarkus-hilla-react'></qwc-quarkus-hilla-browser-callables>`;
    }
}
customElements.define('qwc-quarkus-hilla-react', QwcQuarkusHillaReact);
