import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkMode, emitGenerated, reportDrift } from './generated-file.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(root, 'packages', 'core-web', 'src');
const target = path.join(root, 'apps', 'webos', 'app', 'js');
if (!checkMode) await mkdir(target, { recursive: true });
const drifted = [];
for (const name of ['core.js', 'api.js', 'store.js']) {
  const content = await readFile(path.join(source, name), 'utf8');
  const stale = await emitGenerated(
    path.join(target, name),
    `/* Generated from packages/core-web/src/${name}. Do not edit this copy. */\n${content}`,
  );
  if (stale) drifted.push(stale);
}
reportDrift(drifted);
console.log(
  checkMode
    ? 'Shared web core copies match packages/core-web/src.'
    : 'Synchronized shared web core into apps/webos/app/js.',
);
