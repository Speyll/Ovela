import { spawnSync } from 'node:child_process';
import { copyFile, mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const project = path.join(root, 'apps/android-tv');
const command = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const result = spawnSync(command, [':app:assembleRelease'], {
  cwd: project,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
if (result.status !== 0) process.exit(result.status ?? 1);

const apk = path.join(project, 'app/build/outputs/apk/release/app-release.apk');
const version = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8')).version;
const outDir = path.join(root, 'dist/release');
await mkdir(outDir, { recursive: true });
const target = path.join(outDir, `Ovela-AndroidTV-${version}.apk`);
await copyFile(apk, target);
console.log(`Built ${target}`);
