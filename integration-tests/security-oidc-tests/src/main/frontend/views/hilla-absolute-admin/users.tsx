import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  route: '/hilla-absolute-admin/users',
  title: 'Hilla - Absolute Admin Users',
};

export default function HillaAbsoluteAdminUsersView() {
  return <h2>Hilla - Absolute Admin Users</h2>;
}
