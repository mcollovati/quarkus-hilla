import { useSignal } from '@vaadin/hilla-react-signals';
import { LoginOverlay } from '@vaadin/react-components/LoginOverlay.js';
import React from 'react';
import { useAuth } from 'Frontend/auth';
import { ViewConfig } from "@vaadin/hilla-file-router/types.js";

export const config: ViewConfig = {
    route: 'login',
    menu: { exclude: true}
}

export default function LoginView() {
    const { login } = useAuth();
    const hasError = useSignal(false);

    return (
        <>
            <LoginOverlay
                opened
                error={hasError.value}
                noForgotPassword
                onLogin={async ({ detail: { username, password } }) => {
                    const { error } = await login(username, password);
                    hasError.value = Boolean(error);
                }}
            />
        </>
    );
}
