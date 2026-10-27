import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const pkg = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
const version = pkg.version;
const core = JSON.parse(await readFile(path.join(root, 'packages/core-web/package.json'), 'utf8'));
const webos = JSON.parse(await readFile(path.join(root, 'apps/webos/app/appinfo.json'), 'utf8'));
const bridge = JSON.parse(await readFile(path.join(root, 'apps/api-bridge/package.json'), 'utf8'));
const android = await readFile(path.join(root, 'apps/android-tv/app/build.gradle.kts'), 'utf8');
const appJs = await readFile(path.join(root, 'apps/webos/app/js/app.js'), 'utf8');
const checks = [
  ['package.json', pkg.version],
  ['packages/core-web/package.json', core.version],
  ['webOS appinfo.json', webos.version],
  ['API bridge package.json', bridge.version],
];
for (const [name, value] of checks) {
  if (value !== version) throw new Error(`${name} has ${value}; expected ${version}`);
}
if (!android.includes(`versionName = "${version}"`)) throw new Error('Android versionName does not match package.json');
if (!appJs.includes(`var APP_VERSION = '${version}';`)) throw new Error('webOS APP_VERSION does not match package.json');
console.log(`All platform versions match ${version}.`);
