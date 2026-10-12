type RouterPlugin = {
  name: string;
  configResolved?: unknown;
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
 * Hilla 25.3.2 predates https://github.com/vaadin/hilla/pull/6485.
 * The upstream fix uses an async configResolved hook; leave that implementation alone.
 */
export function generateHillaRoutesBeforeTypeCheck() {
  return {
    name: 'quarkus-hilla-dev-route-generation',
    apply: 'serve' as const,
    configResolved: {
      order: 'post' as const,
      async handler(config: { plugins: readonly RouterPlugin[] }) {
        const router = config.plugins.find((plugin) => plugin.name === 'vite-plugin-file-router');
        if (!router || handler(router.configResolved)?.constructor.name === 'AsyncFunction') {
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
