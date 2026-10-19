import { UserConfigFn } from 'vite';
import { overrideVaadinConfig } from './vite.generated.ts';
import { generateHillaRoutesBeforeTypeCheck } from './hilla-vite-compat.ts';

const customConfig: UserConfigFn = (env) => ({
  plugins: [generateHillaRoutesBeforeTypeCheck()],
  // Here you can add custom Vite parameters
  // https://vitejs.dev/config/
});

export default overrideVaadinConfig(customConfig);
