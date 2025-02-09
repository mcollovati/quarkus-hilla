import '@vaadin/app-layout';
import { AppLayout } from '@vaadin/app-layout';
import '@vaadin/app-layout/vaadin-drawer-toggle';
import '@vaadin/avatar';
import '@vaadin/icon';
import '@vaadin/menu-bar';
import '@vaadin/scroller';
import '@vaadin/side-nav';
import '@vaadin/tabs';
import '@vaadin/tabs/vaadin-tab';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset';
import { html } from 'lit';
import { customElement } from 'lit/decorators.js';
import { router } from '../index.js';
import { views } from '../routes.js';
import { appStore } from '../stores/app-store.js';
import { Layout } from './view.js';

interface RouteInfo {
  path: string;
  title: string;
  icon: string;
}

@customElement('main-layout')
export class MainLayout extends Layout {
  render() {
    return html`
      <vaadin-app-layout primary-section="drawer">
        <header slot="drawer">
          <span class="font-semibold text-l">${appStore.applicationName}</span>
        </header>
        <vaadin-scroller slot="drawer" scroll-direction="vertical">
          <vaadin-side-nav aria-label="${appStore.applicationName}">
            ${this.getMenuRoutes().map(
              (viewRoute) => html`
                <vaadin-side-nav-item path=${router.urlForPath(viewRoute.path)}>
                  <vaadin-icon
                    src="line-awesome/svg/${viewRoute.icon}.svg"
                    slot="prefix"
                    aria-hidden="true"
                    class="p-0"
                  ></vaadin-icon>
                  ${viewRoute.title}
                </vaadin-side-nav-item>
              `,
            )}
          </vaadin-side-nav>
        </vaadin-scroller>

        <footer slot="drawer"></footer>

        <vaadin-drawer-toggle slot="navbar" aria-label="Menu toggle"></vaadin-drawer-toggle>
        <h1 slot="navbar" class="text-l m-0">${appStore.currentViewTitle}</h1>

        <slot></slot>
      </vaadin-app-layout>
    `;
  }

  connectedCallback() {
    super.connectedCallback();
    this.classList.add('block', 'h-full');
    this.reaction(
      () => appStore.location,
      () => {
        AppLayout.dispatchCloseOverlayDrawerEvent();
      },
    );
  }

  private getMenuRoutes(): RouteInfo[] {
    return views.filter((route) => route.title) as RouteInfo[];
  }
}
