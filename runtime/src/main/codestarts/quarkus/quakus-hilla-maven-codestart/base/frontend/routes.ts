import type { Route } from "@vaadin/router";
import "./views/main-view";

export type ViewRoute = Route & {
  title?: string;
  icon?: string;
  children?: ViewRoute[];
};

export const routes: ViewRoute[] = [
  // Place routes below (more info https://hilla.dev/docs/routing)
  {
    path: "",
    component: "main-view",
    icon: "",
    title: "",
  },
];
