import { existsSync } from 'node:fs';
import { mkdir, readFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const app = path.join(root, 'apps/webos/app');
const dist = path.join(root, 'dist/webos');
await mkdir(dist, { recursive: true });
const info = JSON.parse(await readFile(path.join(app, 'appinfo.json'), 'utf8'));
const args = ['--no-minify', '-o', dist, app];
// Prefer the project-local CLI installed under .dev so builds do not depend on
// a global install; fall back to ares-package on PATH.
const localAres = path.join(
  root,
  '.dev/tooling/webos/node_modules/.bin',
  process.platform === 'win32' ? 'ares-package.cmd' : 'ares-package',
);
const result = spawnSync(existsSync(localAres) ? localAres : 'ares-package', args, {
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
if (result.error?.code === 'ENOENT') {
  console.error('ares-package was not found. Install the webOS CLI locally with:');
  console.error('  npm install --prefix .dev/tooling/webos @webos-tools/cli');
  console.error('or globally with: npm install -g @webos-tools/cli');
  process.exit(1);
}
if (result.status !== 0) process.exit(result.status ?? 1);
console.log(`Built ${info.id}_${info.version}_all.ipk`);
