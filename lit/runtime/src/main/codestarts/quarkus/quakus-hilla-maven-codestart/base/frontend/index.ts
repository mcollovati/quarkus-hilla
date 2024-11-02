import { Router } from '@vaadin/router';
import { routes } from './routes.js';

export const router = new Router(document.querySelector('#outlet'));
router.setRoutes(routes);
