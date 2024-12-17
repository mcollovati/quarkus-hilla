import {AppLayout, DrawerToggle, SideNav, SideNavItem, ProgressBar, Button} from '@vaadin/react-components';
import {createMenuItems, useViewConfig} from "@vaadin/hilla-file-router/runtime.js";
import { Signal, signal, effect } from '@vaadin/hilla-react-signals';
import {Suspense, useEffect} from 'react';
import {NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useAuth } from 'Frontend/auth';

const vaadin = window.Vaadin as {
    documentTitleSignal: Signal<string>;
};
vaadin.documentTitleSignal = signal("");
effect(() => {
    document.title = vaadin.documentTitleSignal.value;
});


export default function Layout() {
    const { state, logout } = useAuth();
    const currentTitle = useViewConfig()?.title ?? '';
    const navigate = useNavigate();
    const location = useLocation();
    useEffect(() => {
        vaadin.documentTitleSignal.value = currentTitle;
    })
    return (
        <AppLayout primarySection="drawer">
            <div slot="drawer"
                 className="flex flex-col justify-between h-full p-m">
                <header className="flex flex-col gap-m">
                    <h1 className="text-l m-0">{vaadin.documentTitleSignal}</h1>
                    <SideNav
                        onNavigate={({path}) => navigate(path!)}
                        location={location}
                    >
                        {createMenuItems().map(({to, title}) => (
                            <SideNavItem path={to} key={to}>
                                {title}
                            </SideNavItem>
                        ))}
                    </SideNav>
                </header>
                <footer className="flex flex-col gap-s">
                    {state.user ? (
                        <>
                            <div
                                className="flex items-center gap-s">{state.user.name}</div>
                            <Button onClick={async () => logout()}>Sign
                                out</Button>
                        </>
                    ) : (
                        <NavLink to="/login">Sign in</NavLink>
                    )}
                </footer>
            </div>


            <DrawerToggle slot="navbar" aria-label="Menu toggle"></DrawerToggle>
            <h2 slot="navbar" className="text-l m-0">
                {vaadin.documentTitleSignal}
            </h2>

            <Suspense fallback={<ProgressBar indeterminate className="m-0"/>}>
                <section className="view">
                    <Outlet/>
                </section>
            </Suspense>
        </AppLayout>
    );
}
