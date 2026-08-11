import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { Outlet } from 'react-router';

export const config: ViewConfig = {
  menu: { exclude: true },
  rolesAllowed: ['ADMIN'],
};

export default function AdminHierarchyLayout() {
  return <Outlet />;
}
