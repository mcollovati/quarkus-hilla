import {css, html, LitElement} from 'lit';
import {columnBodyRenderer} from '@vaadin/grid/lit.js';
import {hillaEndpoints as endpoints} from 'build-time-data';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/grid/vaadin-grid-tree-column.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import '@vaadin/icon';

/**
 * This component shows the Rest Easy Reactive Parameter Converter Providers
 */
export class QwcQuarkusHillaEndpoints extends LitElement {

    static styles = css`
        .datatable {
            height: 100%;
            padding-bottom: 10px;
            border: none;
        }
        
        vaadin-grid-tree-toggle {
            width: 100%;
        }

        .annotation {
            color: var(--lumo-contrast-50pct);
        }

        .primary {
            color: var(--lumo-primary-text-color);
        }

        .parameters {
            > ::after {
                content: ",";
            }

            :last-child::after {
                content: "";
            }
        }

        .PermitAll {
            color: var(--lumo-success-text-color);
        }

        .RolesAllowed {
            color: var(--lumo-success-text-color);
        }

        .AnonymousAllowed {
            color: var(--lumo-warning-text-color);
        }

        .DenyAll {
            color: var(--lumo-error-text-color);
        }
    `;

    static properties = {
        _endpoints: {state: true}, _expandedItems: {state: true}
    };

    constructor() {
        super();
        this._endpoints = endpoints;
        this._expandedItems = endpoints;
    }

    render() {
        if (this._endpoints) {
            return this._renderEndpointGrid()
        } else {
            return html`No beans found`;
        }
    }

    _renderEndpointGrid() {
        if (this._endpoints) {
            return html`
                <vaadin-grid .dataProvider="${this._dataProvider}" .expandedItems="${this._expandedItems}"
                             class="datatable" theme="row-stripes">
                    <vaadin-grid-sort-column header="Class"
                                             resizable
                                             ${columnBodyRenderer(this._beanTreeRenderer, [])}
                                             auto-width></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column header="Access" resizable
                                             ${columnBodyRenderer(this._accessAnnotationRenderer, [])}
                                             auto-width></vaadin-grid-sort-column>
                </vaadin-grid>`;
        }
    }

    _dataProvider(params, callback) {
        if (params.parentItem) {
            const children = params.parentItem.children || [];
            callback(children, children.length);
        } else {
            callback(endpoints, endpoints.length);
        }
    }

    _beanTreeRenderer(bean, model) {
        return html`
            <vaadin-grid-tree-toggle
                    .leaf="${!bean.children}"
                    .level="${model.level ?? 0}"
                    .expanded="${true}"
                    @expanded-changed="${(e) => {
                        if (e.detail.value) {
                            this._expandedItems = [...this._expandedItems, bean];
                        } else {
                            this._expandedItems = this._expandedItems.filter((b) => b.declaringClass.name !== bean.declaringClass.name);
                        }
                    }}">
                ${this._beanRenderer(bean)}
            </vaadin-grid-tree-toggle>
        `
    }

    _beanRenderer(bean) {
        return bean.endpointAnnotation ? html`
            <vaadin-vertical-layout>
                <code class="annotation">@${bean.endpointAnnotation}</code>
                <qui-ide-link fileName='${bean.declaringClass.name}' lineNumber=0>
                    <code class="primary" id="${bean.declaringClass.name}">${bean.declaringClass.simpleName}</code>
                    <vaadin-tooltip text="${bean.declaringClass.name}" for="${bean.declaringClass.name}"
                                    position="top"></vaadin-tooltip>
                </qui-ide-link>
            </vaadin-vertical-layout>` : html`
            <qui-ide-link fileName='${bean.declaringClass.name}' lineNumber=0>
                ${this._typeRenderer(bean.methodDescriptor.returnType)}
                <code class="primary">${bean.methodDescriptor.methodName}</code>
                <span class="parameters">(${bean.methodDescriptor.parameters.map(p => this._parameterRenderer(p))}
                    )</span>
            </qui-ide-link>`;
    }

    _parameterRenderer(parameter) {
        return html`
            <span>
                ${this._typeRenderer(parameter.type)}
                ${parameter.name ? html`<code class="primary">${parameter.name}</code>` : ''}
            </span>
        `;
    }

    _typeRenderer(type) {
        return html`
            <span>
                ${type.annotations.map(a => html`<span class="annotation">${a}</span>`)}
                <span class="type">${type.type}</span>
                ${type.generics ? html`<span>&lt;${type.generics.map(g => this._typeRenderer(g))}&gt;</span>` : ''}
            </span>
        `;
    }

    _accessAnnotationRenderer(bean) {
        return html`
            <vaadin-horizontal-layout style="gap: var(--lumo-space-s);" class="${bean.accessAnnotation.name.simpleName}">
                <vaadin-icon icon="${this._accessAnnotationToIcon(bean.accessAnnotation)}"></vaadin-icon>
                <code>${this._shortenAccessAnnotation(bean.accessAnnotation)}</code>
            </vaadin-horizontal-layout>
        `;
    }

    _accessAnnotationToIcon(annotation) {
        switch (annotation.name.simpleName) {
            case "PermitAll":
                return "font-awesome-solid:user-check";
            case "RolesAllowed":
                return "font-awesome-solid:user-group";
            case "AnonymousAllowed":
                return "font-awesome-solid:user-secret";
            case "DenyAll":
                return "font-awesome-solid:lock";
            default:
                return "font-awesome-solid:person-circle-question";
        }
    }

    _shortenAccessAnnotation(annotation) {
        switch (annotation.name.simpleName) {
            case "PermitAll":
                return "Authenticated";
            case "RolesAllowed":
                return annotation.roles.join(', ');
            case "AnonymousAllowed":
                return "Anonymous";
            case "DenyAll":
                return "Denied";
            default:
                return annotation.name.simpleName;
        }
    }

}

customElements.define('qwc-quarkus-hilla-endpoints', QwcQuarkusHillaEndpoints);
