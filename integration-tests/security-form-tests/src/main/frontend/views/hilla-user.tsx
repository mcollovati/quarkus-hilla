import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  menu: { order: 3, icon: 'line-awesome/svg/lock-solid.svg' },
  title: 'Hilla - User',
  rolesAllowed: ['USER'],
};

export default function HillaUserView() {
  return (
      <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
          <img style={{width: '200px'}} src="images/empty-plant.png"/>
          <h2>Hilla - User</h2>
          <p>Only users with role USER see this page</p>
      </div>
  );
}
