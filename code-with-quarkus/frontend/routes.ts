import type {Commands, Context, Route} from '@vaadin/router';
import './views/helloworld/hello-world-view';
import './views/main-layout';
import './views/login/login-view';
import {Flow} from "@vaadin/flow-frontend";
import {uiStore} from "Frontend/stores/ui-store";
import {checkAuthentication, logout} from "Frontend/auth";


const {serverSideRoutes} = new Flow({
    imports: () => import('../target/frontend/generated-flow-imports')
});

export type ViewRoute = Route & {
    title?: string;
    icon?: string;
    children?: ViewRoute[];
};

export const views: ViewRoute[] = [
    // place routes below (more info https://hilla.dev/docs/routing)
    {
        path: '',
        component: 'hello-world-view',
        icon: '',
        title: '',
    },
    {
        path: 'hello',
        component: 'hello-world-view',
        icon: 'la la-globe',
        title: 'Hello World',
    },
    {
        path: 'about',
        component: 'about-view',
        icon: 'la la-file',
        title: 'About',
        action: async (_context, _command) => {
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
    {
        path: 'logout',
        action: async (_: Context, commands: Commands) => {
            //await uiStore.logout();
            await logout();
            return commands.redirect('/login');
        },
    },
    {
        path: '',
        component: 'main-layout',
        children: views,
        action: async (ctx: Context, commands: Commands) => {
            console.log("=============== action ", ctx, views);
            // Hack to find client side views
            if (views.map((view) => "/" + view.path).includes(ctx.pathname)) {
                const authenticated = await checkAuthentication();
                if (!authenticated) {
                    return commands.redirect('/login');
                }
            }
            return undefined;
        },
    },
];
