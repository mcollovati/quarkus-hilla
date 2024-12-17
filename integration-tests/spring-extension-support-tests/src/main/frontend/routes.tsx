import HelloWorldView from 'Frontend/views/helloworld/HelloWorldView.js';
import AutoGridView from 'Frontend/views/autogrid/AutoGridView.js';
import MainLayout from 'Frontend/views/MainLayout.js';
import { lazy } from 'react';
import { createBrowserRouter, RouteObject } from 'react-router';
import AutoCrudView from "Frontend/views/autocrud/AutoCrudView";
import AutoFormView from "Frontend/views/autoform/AutoFormView";

const AboutView = lazy(async () => import('Frontend/views/about/AboutView.js'));

export const routes: RouteObject[] = [
  {
    element: <MainLayout />,
    handle: { title: 'Main' },
    children: [
      { path: '/', element: <HelloWorldView />, handle: { title: 'Hello World' } },
      { path: '/auto-grid', element: <AutoGridView />, handle: { title: 'Auto Grid' } },
      { path: '/auto-form', element: <AutoFormView />, handle: { title: 'Auto Form' } },
      { path: '/auto-crud', element: <AutoCrudView />, handle: { title: 'Auto Crud' } },
      { path: '/about', element: <AboutView />, handle: { title: 'About' } },
    ],
  },
];

export default createBrowserRouter(routes);
