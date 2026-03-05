import * as esbuild from 'esbuild';

await esbuild.build({
  entryPoints: ['src/lib/index.ts'],
  bundle: true,
  outfile: 'public/pkpd-bundle.js',
  format: 'iife',
  globalName: 'PKPD',
  platform: 'browser',
  target: 'es2020',
  minify: false,
  sourcemap: true
});

console.log('✓ Bundle created: public/pkpd-bundle.js');
