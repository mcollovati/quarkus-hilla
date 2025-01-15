import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  menu: { order: 2, icon: 'line-awesome/svg/lock-solid.svg' },
  title: 'Hilla - Admin',
  rolesAllowed: ['ADMIN'],
};

export default function HillaAdminView() {
  return (
      <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
          <img style={{width: '200px'}} src="images/empty-plant.png"/>
          <h2>Hilla - Admin</h2>
          <p>Only users with role ADMIN see this page</p>
      </div>
  );
}
