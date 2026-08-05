import { ViewConfig } from '@vaadin/hilla-file-router/types.js';

export const config: ViewConfig = {
  route: 'hilla-wildcard/*/:id?',
  title: 'Hilla - Wildcard',
  rolesAllowed: ['ADMIN'],
};

export default function HillaWildcardView() {
  return <h2>Hilla - Wildcard</h2>;
}
