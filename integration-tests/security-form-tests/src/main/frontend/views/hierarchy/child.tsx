import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  menu: { exclude: true },
  title: 'Hilla hierarchy child',
};

export default function HillaHierarchyChildView() {
  return <h2>Hilla hierarchy child</h2>;
}
