import {RouterLocation} from '@vaadin/router';
import {makeAutoObservable} from 'mobx';
import UserInfo
    from "Frontend/generated/com/example/application/entities/UserInfo";
import {UserInfoEndpoint} from "Frontend/generated/endpoints";

export class AppStore {
    applicationName = 'my-hilla-app';

    // The location, relative to the base path, e.g. "hello" when viewing "/hello"
    location = '';

    currentViewTitle = '';

    user: UserInfo | undefined = undefined;

    constructor() {
        makeAutoObservable(this);
    }

    setLocation(location: RouterLocation) {
        const serverSideRoute = location.route?.path == '(.*)';
        if (location.route && !serverSideRoute) {
            this.location = location.route.path;
        } else if (location.pathname.startsWith(location.baseUrl)) {
            this.location = location.pathname.substr(location.baseUrl.length);
        } else {
            this.location = location.pathname;
        }
        if (serverSideRoute) {
            this.currentViewTitle = document.title; // Title set by server
        } else {
            this.currentViewTitle = (location?.route as any)?.title || '';
        }
    }

    async fetchUserInfo() {
        this.user = await UserInfoEndpoint.me();
    }

    clearUserInfo() {
        this.user = undefined;
    }

    get loggedIn() {
        return !!this.user;
    }

    isUserInRole(role: string) {
        return this.user?.roles?.includes(role);
    }

}

export const appStore = new AppStore();
