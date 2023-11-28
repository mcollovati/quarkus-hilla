import MainView from "Frontend/views/MainView.js";
import {
    createBrowserRouter,
    RouteObject
} from "react-router-dom";

export const routes: readonly RouteObject[] = [
  { path: "/", element: <MainView /> },
];

export const router = createBrowserRouter([...routes], {basename: new URL(document.baseURI).pathname });
