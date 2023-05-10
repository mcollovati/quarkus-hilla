import type {Commands, Context, Route} from '@vaadin/router';
import './views/helloworld/hello-world-view';
import './views/main-layout';
import './views/login/login-view';
import {Flow} from "@vaadin/flow-frontend";
import {appStore} from "Frontend/stores/app-store";


const {serverSideRoutes} = new Flow({
    imports: () => import('../target/frontend/generated-flow-imports')
});

export type ViewRoute = Route & {
    title?: string;
    icon?: string;
    requiresLogin?: boolean;
    rolesAllowed?: string[];
    children?: ViewRoute[];
};

export const hasAccess = (route: Route) => {
    const viewRoute = route as ViewRoute;
    if (viewRoute.requiresLogin && !appStore.loggedIn) {
        return false;
    }

    if (viewRoute.rolesAllowed) {
        return viewRoute.rolesAllowed.some((role) => appStore.isUserInRole(role));
    }
    return true;
};

const checkAccessAction = async (ctx: Context, cmd: Commands)  => {
    if (views.includes(ctx.route as ViewRoute)) {
        if (!hasAccess(ctx.route)) {
            return cmd.redirect('login');
        }
    }
    return undefined;
}


export const views: ViewRoute[] = [
    // place routes below (more info https://hilla.dev/docs/routing)
    {
        path: '',
        component: 'hello-world-view',
        icon: '',
        title: '',
        requiresLogin: true,
        action: checkAccessAction
    },
    {
        path: 'hello',
        component: 'hello-world-view',
        icon: 'la la-globe',
        title: 'Hello World',
        requiresLogin: true,
        action: checkAccessAction
    },
    {
        path: 'about',
        component: 'about-view',
        icon: 'la la-file',
        title: 'About',
        requiresLogin: false,
        action: async (_context, _command) => {
            await checkAccessAction(_context, _command);
            await import('./views/about/about-view');
            return;
        },
    },
    ...serverSideRoutes
];
export const routes: ViewRoute[] = [
    {
        path: 'login',
        component: 'login-view',
    },
    /*
    {
        path: 'logout',
        action: async (_: Context, commands: Commands) => {
            //await uiStore.logout();
            await logout();
            return commands.redirect('/login');
        },
    },
     */
    {
        path: '',
        component: 'main-layout',
        children: views,
    },
];

