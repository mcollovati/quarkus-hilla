import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  title: 'Hilla - Item',
  rolesAllowed: ['ADMIN'],
};

export default function HillaItemView() {
  return (
    <div className="flex flex-col h-full items-center justify-center p-l text-center box-border">
      <h2>Hilla - Item</h2>
      <p>Only users with role ADMIN see item details</p>
    </div>
  );
}
