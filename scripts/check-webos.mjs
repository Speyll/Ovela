import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const app = path.join(root, 'apps/webos/app');
JSON.parse(await readFile(path.join(app, 'appinfo.json'), 'utf8'));
const files = (await readdir(path.join(app, 'js'))).filter((name) => name.endsWith('.js'));
for (const name of files) {
  const source = await readFile(path.join(app, 'js', name), 'utf8');
  new vm.Script(source, { filename: name });
}

const rootVersion = (await readFile(path.join(root, 'VERSION'), 'utf8')).trim();
const index = await readFile(path.join(app, 'index.html'), 'utf8');
if (rootVersion.startsWith('0.7.')) {
  for (const asset of ['css/v0.7-ui.css', 'js/v0.7-ui.js']) {
    if (!index.includes(asset)) throw new Error(`webOS index.html does not load ${asset}`);
  }
  const v07 = await readFile(path.join(app, 'js', 'v0.7-ui.js'), 'utf8');
  if (!v07.includes(`version:'${rootVersion}'`)) {
    throw new Error(`v0.7 UI module version does not match ${rootVersion}`);
  }
  if (!v07.includes('openSearchPopup') || !v07.includes('liveWindowSize:5') || !v07.includes('favoriteAutoSortEnabled')) {
    throw new Error('v0.7 UI module is missing required release features.');
  }
}

console.log(`Validated appinfo.json, ${files.length} webOS JavaScript files, and release UI assets.`);
