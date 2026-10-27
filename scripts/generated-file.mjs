import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

export const checkMode = process.argv.includes('--check');

/**
 * Write a generated file, or in check mode compare it without touching the
 * working tree. Returns the drifted path, or null when the file is current.
 */
export async function emitGenerated(file, content) {
  if (!checkMode) {
    await writeFile(file, content);
    return null;
  }
  let current = null;
  try {
    current = await readFile(file, 'utf8');
  } catch {
    current = null;
  }
  return current === content ? null : path.relative(process.cwd(), file);
}

export function reportDrift(drifted) {
  if (drifted.length === 0) return;
  for (const file of drifted) console.error(`Out of date: ${file}`);
  console.error('Run `npm run prepare:apps` to regenerate generated files.');
  process.exit(1);
}
