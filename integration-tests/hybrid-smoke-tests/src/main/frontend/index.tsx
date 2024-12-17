import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { router } from 'Frontend/generated/routes.js';
import { AuthProvider } from './auth';

function App() {
    return <AuthProvider><RouterProvider router={router} /></AuthProvider>;
}

const outlet = document.getElementById('outlet')!;
let root = (outlet as any)._root ?? createRoot(outlet);
(outlet as any)._root = root;
root.render(createElement(App));

