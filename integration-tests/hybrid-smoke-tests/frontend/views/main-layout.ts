import '@vaadin-component-factory/vcf-nav';
import '@vaadin/app-layout';
import {AppLayout} from '@vaadin/app-layout';
import '@vaadin/app-layout/vaadin-drawer-toggle';
import '@vaadin/avatar';
import '@vaadin/icon';
import '@vaadin/menu-bar';
import type { MenuBarItem, MenuBarItemSelectedEvent } from '@vaadin/menu-bar';
import '@vaadin/scroller';
import '@vaadin/tabs';
import '@vaadin/tabs/vaadin-tab';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset';
import {html, render} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {router} from '../index';
import {hasAccess, views} from '../routes';
import {appStore} from '../stores/app-store';
import {Layout} from './view';
import {logout} from "Frontend/auth";
import UserInfo
    from "Frontend/generated/com/example/application/entities/UserInfo";


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
                    <h1 class="text-l m-0">${appStore.applicationName}</h1>
                </header>
                <vaadin-scroller slot="drawer" scroll-direction="vertical">
                    <!-- vcf-nav is not yet an official component -->
                    <!-- For documentation, visit https://github.com/vaadin/vcf-nav#readme -->
                    <vcf-nav aria-label="${appStore.applicationName}">
                        ${this.getMenuRoutes().map(
                                (viewRoute) => html`
                                    <vcf-nav-item
                                            path=${router.urlForPath(viewRoute.path)}>
                                        <span class="${viewRoute.icon} nav-item-icon"
                                              slot="prefix"
                                              aria-hidden="true"></span>
                                        ${viewRoute.title}
                                    </vcf-nav-item>
                                `
                        )}
                        <vcf-nav-item path=${router.urlForPath('flow-view')}>
                            <span class="la la-globe nav-item-icon"
                                  slot="prefix" aria-hidden="true"></span>
                            Flow View
                        </vcf-nav-item>
                    </vcf-nav>
                </vaadin-scroller>

                <footer slot="drawer">
                    ${appStore.user
                            ? html`
                <vaadin-menu-bar
                  theme="tertiary-inline contrast"
                  .items="${this.getUserMenuItems(appStore.user)}"
                  @item-selected="${this.userMenuItemSelected}"
                ></vaadin-menu-bar>
              `
                            : html`<a router-ignore href="login">Sign in</a>`}
                </footer>

                <vaadin-drawer-toggle slot="navbar"
                                      aria-label="Menu toggle"></vaadin-drawer-toggle>
                <h2 slot="navbar" class="text-l m-0">
                    ${appStore.currentViewTitle}</h2>

                <slot></slot>
            </vaadin-app-layout>
        `;
    }

    private renderUserInfo() {
        return appStore.user ? html`
            <div>
                ${appStore.user.name}
                <a href="${router.urlForPath('/logout')}">Logout</a>
                <a href="${logout()}">Logout2</a>
            </div>
        ` : html`<a router-ignore href="login">Sign in</a>`;
    }

    async connectedCallback() {
        super.connectedCallback();
        this.classList.add('block', 'h-full');
        this.reaction(
            () => appStore.location,
            () => {
                AppLayout.dispatchCloseOverlayDrawerEvent();
            }
        );
    }

    private getMenuRoutes(): RouteInfo[] {
        return views.filter((route) => route.title)
            .filter((route) => hasAccess(route)) as RouteInfo[];
    }

    private getUserMenuItems(user: UserInfo): MenuBarItem[] {
        return [
            {
                component: this.createUserMenuItem(user),
                children: [{ text: 'Sign out' }],
            },
        ];
    }

    private createUserMenuItem(user: UserInfo) {
        const item = document.createElement('div');
        item.style.display = 'flex';
        item.style.alignItems = 'center';
        item.style.gap = 'var(--lumo-space-s)';
        render(
            html`
        <span>${user.name}</span>
        <vaadin-icon icon="lumo:dropdown"></vaadin-icon>
      `,
            item
        );
        return item;
    }

    private userMenuItemSelected(e: MenuBarItemSelectedEvent) {
        if (e.detail.value.text === 'Sign out') {
            logout();
        }
    }

}
