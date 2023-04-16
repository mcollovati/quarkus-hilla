import '@vaadin-component-factory/vcf-nav';
import '@vaadin/app-layout';
import {AppLayout} from '@vaadin/app-layout';
import '@vaadin/app-layout/vaadin-drawer-toggle';
import '@vaadin/avatar';
import '@vaadin/icon';
import '@vaadin/menu-bar';
import '@vaadin/scroller';
import '@vaadin/tabs';
import '@vaadin/tabs/vaadin-tab';
import '@vaadin/vaadin-lumo-styles/vaadin-iconset';
import {html, nothing} from 'lit';
import {customElement, property} from 'lit/decorators.js';
import {router} from '../index';
import {views} from '../routes';
import {appStore} from '../stores/app-store';
import {Layout} from './view';
import {checkAuthentication, isLoggedIn} from "Frontend/auth";
import UserInfo
    from "Frontend/generated/com/example/application/entities/UserInfo";
import userInfo
    from "Frontend/generated/com/example/application/entities/UserInfo";


interface RouteInfo {
    path: string;
    title: string;
    icon: string;
}

@customElement('main-layout')
export class MainLayout extends Layout {

    @property()
    private userInfo: UserInfo | undefined;

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

                <footer slot="drawer">${this.renderUserInfo()}</footer>

                <vaadin-drawer-toggle slot="navbar"
                                      aria-label="Menu toggle"></vaadin-drawer-toggle>
                <h2 slot="navbar" class="text-l m-0">
                    ${appStore.currentViewTitle}</h2>

                <slot></slot>
            </vaadin-app-layout>
        `;
    }

    private renderUserInfo() {
        console.log("===============0 renderUserInfo ", this.userInfo);
        return this.userInfo ? html`
            <div>
                ${this.userInfo.name}
                <a href="${router.urlForPath('/logout')}">Logout</a>
            </div>
        ` : html`<span>OOOOPS</span>`;
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
        console.log("================ Logged in? ", isLoggedIn())
        if (isLoggedIn()) {
            this.userInfo = await checkAuthentication().then( (auth) => auth?.user)
            console.log("================ Logged in!!! ", this.userInfo)
        }
    }

    private getMenuRoutes(): RouteInfo[] {
        return views.filter((route) => route.title) as RouteInfo[];
    }
}
