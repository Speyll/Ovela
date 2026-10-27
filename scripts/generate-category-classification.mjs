import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { checkMode, emitGenerated, reportDrift } from './generated-file.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const config = JSON.parse(await readFile(path.join(root, 'packages/category-classification.json'), 'utf8'));

const kotlinString = (value) => JSON.stringify(String(value)).replace(/\$/g, '\\$');
const kotlinRule = (rule) =>
  `    CategoryAliasRule(${kotlinString(rule.key)}, ${kotlinString(rule.label)}, listOf(${rule.aliases
    .map(kotlinString)
    .join(', ')})${rule.rank === undefined ? '' : `, ${Number(rule.rank)}`}),`;

const lines = [
  '/* Generated from packages/category-classification.json. Do not edit this copy. */',
  'package io.github.speyll.ovela.ui',
  '',
  'internal val categoryLanguageRules: List<CategoryAliasRule> = listOf(',
  ...config.languages.map(kotlinRule),
  ')',
  '',
  'internal val categoryQualityRules: List<CategoryAliasRule> = listOf(',
  ...config.qualities.map(kotlinRule),
  ')',
  '',
  'internal val categoryFormatRules: List<CategoryAliasRule> = listOf(',
  ...config.formats.map(kotlinRule),
  ')',
  '',
  'internal val categoryRegionRules: List<CategoryAliasRule> = listOf(',
  ...config.regions.map(kotlinRule),
  ')',
  '',
  'internal val categoryProviderRules: List<CategoryAliasRule> = listOf(',
  ...config.providers.map(kotlinRule),
  ')',
  '',
];
const drifted = [];
const rulesPath = path.join(root, 'apps/android-tv/app/src/main/java/io/github/speyll/ovela/ui/CategoryClassificationRules.kt');
const staleRules = await emitGenerated(rulesPath, lines.join('\n'));
if (staleRules) drifted.push(staleRules);

const corePath = path.join(root, 'packages/core-web/src/core.js');
let core = await readFile(corePath, 'utf8');
const begin = '  // BEGIN GENERATED CATEGORY CLASSIFICATION';
const end = '  // END GENERATED CATEGORY CLASSIFICATION';
const pattern = new RegExp(`${begin.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[\\s\\S]*?${end.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}`);
if (!pattern.test(core)) throw new Error('category classification markers are missing from packages/core-web/src/core.js');
const serialized = JSON.stringify(config, null, 2).split('\n');
const block = [
  begin,
  '  var CATEGORY_CLASSIFICATION_RULES = ' + serialized[0],
  ...serialized.slice(1, -1).map((line) => '  ' + line),
  '  ' + serialized[serialized.length - 1] + ';',
  end,
].join('\n');
core = core.replace(pattern, block);
const staleCore = await emitGenerated(corePath, core);
if (staleCore) drifted.push(staleCore);
reportDrift(drifted);
console.log(
  checkMode
    ? 'Category classification outputs match packages/category-classification.json.'
    : 'Generated Android and web category classification rules.',
);
