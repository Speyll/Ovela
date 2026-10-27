import { readFile } from 'node:fs/promises';
const version = (await readFile(new URL('../VERSION', import.meta.url), 'utf8')).trim();
const changelog = await readFile(new URL('../CHANGELOG.md', import.meta.url), 'utf8');

// Print the changelog section for the current VERSION so tag releases get
// useful notes even when docs/release-notes/<version>.md is absent.
const lines = changelog.split('\n');
const start = lines.findIndex((line) => line === `## ${version}` || line.startsWith(`## ${version} `));
const body = [];
if (start >= 0) {
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^## /.test(lines[index])) break;
    body.push(lines[index]);
  }
}
const notes = body.join('\n').trim();
console.log(`# Ovela v${version}\n\n${notes || 'See CHANGELOG.md for platform status and changes.'}`);
