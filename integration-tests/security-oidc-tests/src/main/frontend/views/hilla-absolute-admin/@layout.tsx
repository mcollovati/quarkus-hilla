import { ViewConfig } from '@vaadin/hilla-file-router/types.js';
import { Outlet } from 'react-router';

export const config: ViewConfig = {
  rolesAllowed: ['ADMIN'],
};

export default function HillaAbsoluteAdminLayout() {
  return <Outlet />;
}
