import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  menu: { order: 1, icon: 'line-awesome/svg/lock-solid.svg' },
  title: 'Hilla - Authenticated',
  loginRequired: true,
};

export default function HillaAuthenticatedView() {
  return (
    <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
      <h2>Hilla - Authenticated</h2>
      <p>All authenticated users can see this page</p>
    </div>
  );
}
