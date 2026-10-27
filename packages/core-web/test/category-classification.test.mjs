import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const core = require('../src/core.js');

test('classifies prefixes, full words, accents, and IDs without substring false positives', () => {
  const arabic = core.classifyCategory('AR| News');
  assert.equal(arabic.language.key, 'ar');
  assert.equal(arabic.groupKey, 'AR');

  const accented = core.classifyCategory('ESPAÑA 4K | Deportes');
  assert.equal(accented.language.key, 'es');
  assert.equal(accented.quality.key, '4k');
  assert.equal(accented.region.label, 'Spain');
  assert.equal(accented.groupLabel, 'ESPAÑA 4K');

  const fullWord = core.classifyCategory('FRENCH MOVIES');
  assert.equal(fullWord.language.key, 'fr');
  assert.equal(fullWord.groupKey, 'FRENCH');

  assert.equal(core.classifyCategory('Entertainment').language, null);
  assert.equal(core.classifyCategory('No prefix at all').groupKey, core.CATEGORY_OTHER_GROUP);
  assert.equal(core.classifyCategory('IT Movies').language, null);

  const ambiguous = core.classifyCategory('AR EN| Mixed');
  assert.equal(ambiguous.language, null);
  assert.equal(ambiguous.ambiguousLanguage, true);
  assert.ok(ambiguous.summary.includes('Multiple languages'));
});

test('facets remain independent and unknown values are explicit', () => {
  const categories = [
    { category_name: 'ENGLISH | News' },
    { category_name: 'FRANCE| Cinéma' },
    { category_name: '4K| UHD' },
    { category_name: 'Plain' },
  ];
  assert.deepEqual(
    core.categoryFacets(categories, 'language').map(option => [option.label, option.count]),
    [['English', 1], ['French', 1], ['Unclassified', 2]],
  );
  assert.deepEqual(
    core.categoryFacets(categories, 'quality').map(option => [option.label, option.count]),
    [['4K', 1], ['Unclassified', 3]],
  );
  assert.equal(core.categoryMatchesFacet('4K| UHD', 'quality', '4k'), true);
  assert.equal(core.categoryMatchesFacet('4K| UHD', 'language', core.CATEGORY_UNCLASSIFIED_FACET), true);
});

test('classification stays fast and its derived cache remains bounded', () => {
  const names = [
    'AR| News', 'FRANCE| Cinéma', 'ENGLISH MOVIES', 'FRENCH 4K HEVC',
    'TURKISH DRAMA', 'Documentary SD', 'NETFLIX| Originals', 'Plain News',
  ];
  const start = performance.now();
  for (let index = 0; index < 50_000; index += 1) {
    core.classifyCategory(names[index % names.length]);
  }
  const elapsed = performance.now() - start;
  assert.ok(elapsed < 2_000, `warm classification took ${elapsed.toFixed(1)}ms`);

  for (let index = 0; index < 2_000; index += 1) {
    core.classifyCategory(`Unknown provider category ${index}`);
  }
  const cache = core.categoryClassificationCacheInfo();
  assert.equal(cache.limit, 512);
  assert.ok(cache.size <= cache.limit, `cache size ${cache.size}`);
});
