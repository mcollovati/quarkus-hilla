import {login as serverLogin, logout as serverLogout,} from '@hilla/frontend';
import {makeAutoObservable} from "mobx";

export class UiStore {

    loggedIn = true;

    constructor() {
        makeAutoObservable(this);
    }

    async login(username: string, password: string) {
        const result = await serverLogin(username, password);
        if (!result.error) {
            this.setLoggedIn(true);
        } else {
            throw new Error(result.errorMessage || 'Login failed');
        }
    }

    async logout() {
        await serverLogout();
        this.setLoggedIn(false);
    }

    setLoggedIn(loggedIn: boolean) {
        this.loggedIn = loggedIn;
        if (loggedIn) {
            console.log("logged in");
            //appStore.initFromServer();
        }
    }
}

export const uiStore = new UiStore();
