import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';

type RouterPlugin = {
  name: string;
  buildStart?: unknown;
};

function handler(hook: unknown): Function | undefined {
  if (typeof hook === 'function') {
    return hook;
  }
  if (hook && typeof hook === 'object' && 'handler' in hook && typeof hook.handler === 'function') {
    return hook.handler;
  }
  return undefined;
}

/**
 * Generate Hilla routes before the dev TypeScript checker starts.
 * Hilla 25.3.0–25.3.2 are affected by https://github.com/vaadin/hilla/issues/6040.
 * Apply only to the verified affected package; newer upstream implementations stay untouched.
 */
export function generateHillaRoutesBeforeTypeCheck() {
  return {
    name: 'quarkus-hilla-dev-route-generation',
    apply: 'serve' as const,
    configResolved: {
      order: 'post' as const,
      async handler(config: { root: string; logger: { warn(message: string): void }; plugins: readonly RouterPlugin[] }) {
        const router = config.plugins.find((plugin) => plugin.name === 'vite-plugin-file-router');
        if (!router) {
          return;
        }
        let version: string;
        try {
          const require = createRequire(resolve(config.root, 'package.json'));
          const routerPath = require.resolve('@vaadin/hilla-file-router/vite-plugin.js');
          ({ version } = JSON.parse(readFileSync(join(dirname(routerPath), 'package.json'), 'utf8')));
        } catch {
          config.logger.warn('Hilla route compatibility helper could not resolve the router package; skipping early generation.');
          return;
        }
        if (!['25.3.0', '25.3.1', '25.3.2'].includes(version)) {
          return;
        }
        // The legacy Hilla hook only generates files; it does not use a build context.
        const generate = handler(router.buildStart);
        if (generate) {
          await Reflect.apply(generate, undefined, []);
        }
      },
    },
  };
}
