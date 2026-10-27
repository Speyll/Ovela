import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (file) => readFile(path.join(root, file), 'utf8').catch(() => null);

const apache = await read('LICENSE');
const mit = await read('licenses/XtreamlyTV-MIT.txt');
if (!apache) throw new Error('LICENSE is missing.');
if (!mit) throw new Error('licenses/XtreamlyTV-MIT.txt is missing.');

const copies = [
  ['apps/webos/app/assets/licenses/Apache-2.0.txt', apache, 'LICENSE'],
  ['apps/android-tv/app/src/main/assets/licenses/Apache-2.0.txt', apache, 'LICENSE'],
  ['apps/webos/app/assets/licenses/XtreamlyTV-MIT.txt', mit, 'licenses/XtreamlyTV-MIT.txt'],
  ['apps/android-tv/app/src/main/assets/licenses/XtreamlyTV-MIT.txt', mit, 'licenses/XtreamlyTV-MIT.txt'],
];

for (const [file, expected, source] of copies) {
  const actual = await read(file);
  if (actual === null) throw new Error(`${file} is missing. Copy ${source} into the packaged assets.`);
  if (actual !== expected) throw new Error(`${file} does not match ${source}. Copy the current licence into the packaged assets.`);
}
console.log('Packaged licence notices match LICENSE and licenses/XtreamlyTV-MIT.txt.');
