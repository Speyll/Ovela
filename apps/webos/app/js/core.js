/* Generated from packages/core-web/src/core.js. Do not edit this copy. */
(function (root, factory) {
  'use strict';
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.OvelaCore = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';

  // BEGIN GENERATED CATEGORY CLASSIFICATION
  var CATEGORY_CLASSIFICATION_RULES = {
    "version": 1,
    "languages": [
      {
        "key": "ar",
        "label": "Arabic",
        "aliases": [
          "ar",
          "arab",
          "arabic",
          "arabe",
          "arabisch",
          "arabiya"
        ]
      },
      {
        "key": "en",
        "label": "English",
        "aliases": [
          "en",
          "eng",
          "english",
          "anglais",
          "ingles",
          "england",
          "britain",
          "british",
          "uk",
          "gb"
        ]
      },
      {
        "key": "fr",
        "label": "French",
        "aliases": [
          "fr",
          "french",
          "francais",
          "francophone",
          "france"
        ]
      },
      {
        "key": "es",
        "label": "Spanish",
        "aliases": [
          "es",
          "spanish",
          "espanol",
          "espana",
          "castellano",
          "latino",
          "spain"
        ]
      },
      {
        "key": "de",
        "label": "German",
        "aliases": [
          "de",
          "german",
          "deutsch",
          "deutschland",
          "germany"
        ]
      },
      {
        "key": "it",
        "label": "Italian",
        "aliases": [
          "it",
          "italian",
          "italiano",
          "italia",
          "italy"
        ]
      },
      {
        "key": "pt",
        "label": "Portuguese",
        "aliases": [
          "pt",
          "portuguese",
          "portugues",
          "portugal",
          "brasileiro",
          "brasil",
          "brazil"
        ]
      },
      {
        "key": "tr",
        "label": "Turkish",
        "aliases": [
          "tr",
          "turkish",
          "turkce",
          "turkiye",
          "turk",
          "turkey"
        ]
      },
      {
        "key": "ru",
        "label": "Russian",
        "aliases": [
          "ru",
          "russian",
          "russkiy",
          "russkij",
          "rossiya",
          "russia"
        ]
      },
      {
        "key": "pl",
        "label": "Polish",
        "aliases": [
          "pl",
          "polish",
          "polski",
          "polska",
          "poland"
        ]
      },
      {
        "key": "nl",
        "label": "Dutch",
        "aliases": [
          "nl",
          "dutch",
          "nederlands",
          "nederland",
          "holland",
          "netherlands"
        ]
      },
      {
        "key": "ro",
        "label": "Romanian",
        "aliases": [
          "ro",
          "romanian",
          "romana",
          "romania"
        ]
      },
      {
        "key": "sq",
        "label": "Albanian",
        "aliases": [
          "al",
          "albanian",
          "shqip",
          "shqiptar",
          "albania"
        ]
      },
      {
        "key": "el",
        "label": "Greek",
        "aliases": [
          "gr",
          "greek",
          "ellinika",
          "hellenic",
          "greece"
        ]
      },
      {
        "key": "sv",
        "label": "Swedish",
        "aliases": [
          "se",
          "swedish",
          "svenska",
          "sverige",
          "sweden"
        ]
      },
      {
        "key": "no",
        "label": "Norwegian",
        "aliases": [
          "no",
          "norwegian",
          "norsk",
          "norge",
          "norway"
        ]
      },
      {
        "key": "da",
        "label": "Danish",
        "aliases": [
          "dk",
          "danish",
          "dansk",
          "danmark",
          "denmark"
        ]
      },
      {
        "key": "fi",
        "label": "Finnish",
        "aliases": [
          "fi",
          "finnish",
          "suomi",
          "finland"
        ]
      },
      {
        "key": "cs",
        "label": "Czech",
        "aliases": [
          "cz",
          "czech",
          "cesky",
          "ceska",
          "czechia"
        ]
      },
      {
        "key": "hu",
        "label": "Hungarian",
        "aliases": [
          "hu",
          "hungarian",
          "magyar",
          "hungary"
        ]
      },
      {
        "key": "bg",
        "label": "Bulgarian",
        "aliases": [
          "bg",
          "bulgarian",
          "balgarski",
          "bulgaria"
        ]
      },
      {
        "key": "hr",
        "label": "Croatian",
        "aliases": [
          "hr",
          "croatian",
          "hrvatski",
          "hrvatska",
          "croatia"
        ]
      },
      {
        "key": "sr",
        "label": "Serbian",
        "aliases": [
          "rs",
          "serbian",
          "srpski",
          "srbija",
          "serbia"
        ]
      },
      {
        "key": "sk",
        "label": "Slovak",
        "aliases": [
          "sk",
          "slovak",
          "slovensky",
          "slovensko",
          "slovakia"
        ]
      },
      {
        "key": "sl",
        "label": "Slovenian",
        "aliases": [
          "si",
          "slovenian",
          "slovenski",
          "slovenija",
          "slovenia"
        ]
      },
      {
        "key": "uk",
        "label": "Ukrainian",
        "aliases": [
          "ua",
          "ukrainian",
          "ukrainska",
          "ukraina",
          "ukraine"
        ]
      },
      {
        "key": "he",
        "label": "Hebrew",
        "aliases": [
          "he",
          "il",
          "hebrew",
          "ivrit",
          "israel"
        ]
      },
      {
        "key": "hi",
        "label": "Hindi",
        "aliases": [
          "hi",
          "hindi"
        ]
      },
      {
        "key": "ur",
        "label": "Urdu",
        "aliases": [
          "ur",
          "urdu"
        ]
      },
      {
        "key": "fa",
        "label": "Persian",
        "aliases": [
          "fa",
          "persian",
          "farsi",
          "iran"
        ]
      },
      {
        "key": "ku",
        "label": "Kurdish",
        "aliases": [
          "ku",
          "kurdish",
          "kurdi"
        ]
      },
      {
        "key": "tl",
        "label": "Filipino",
        "aliases": [
          "tl",
          "filipino",
          "tagalog",
          "philippines"
        ]
      },
      {
        "key": "vi",
        "label": "Vietnamese",
        "aliases": [
          "vi",
          "vietnamese",
          "vietnam"
        ]
      },
      {
        "key": "id",
        "label": "Indonesian",
        "aliases": [
          "id",
          "indonesian",
          "indonesia"
        ]
      },
      {
        "key": "ms",
        "label": "Malay",
        "aliases": [
          "ms",
          "malay",
          "malaysia"
        ]
      },
      {
        "key": "th",
        "label": "Thai",
        "aliases": [
          "th",
          "thai",
          "thailand"
        ]
      },
      {
        "key": "ja",
        "label": "Japanese",
        "aliases": [
          "ja",
          "japanese",
          "japan"
        ]
      },
      {
        "key": "ko",
        "label": "Korean",
        "aliases": [
          "ko",
          "korean",
          "korea"
        ]
      },
      {
        "key": "zh",
        "label": "Chinese",
        "aliases": [
          "zh",
          "chinese",
          "mandarin",
          "cantonese",
          "china"
        ]
      }
    ],
    "qualities": [
      {
        "key": "8k",
        "label": "8K",
        "rank": 50,
        "aliases": [
          "8k",
          "4320p"
        ]
      },
      {
        "key": "4k",
        "label": "4K",
        "rank": 40,
        "aliases": [
          "4k",
          "uhd",
          "2160p",
          "2160",
          "ultra hd"
        ]
      },
      {
        "key": "fhd",
        "label": "FHD",
        "rank": 30,
        "aliases": [
          "fhd",
          "full hd",
          "fullhd",
          "1080p",
          "1080"
        ]
      },
      {
        "key": "hd",
        "label": "HD",
        "rank": 20,
        "aliases": [
          "hd",
          "720p",
          "720"
        ]
      },
      {
        "key": "sd",
        "label": "SD",
        "rank": 10,
        "aliases": [
          "sd",
          "480p",
          "480",
          "360p"
        ]
      }
    ],
    "formats": [
      {
        "key": "hevc",
        "label": "HEVC",
        "aliases": [
          "hevc",
          "h265",
          "h 265",
          "x265"
        ]
      },
      {
        "key": "avc",
        "label": "AVC",
        "aliases": [
          "avc",
          "h264",
          "h 264",
          "x264"
        ]
      },
      {
        "key": "hdr",
        "label": "HDR",
        "aliases": [
          "hdr",
          "hdr10",
          "dolby vision"
        ]
      }
    ],
    "regions": [
      {
        "key": "es",
        "label": "Spain",
        "aliases": [
          "es",
          "espana",
          "spain",
          "espagne"
        ]
      },
      {
        "key": "fr",
        "label": "France",
        "aliases": [
          "fr",
          "france",
          "francia"
        ]
      },
      {
        "key": "gb",
        "label": "United Kingdom",
        "aliases": [
          "uk",
          "gb",
          "united kingdom",
          "england",
          "britain"
        ]
      },
      {
        "key": "us",
        "label": "United States",
        "aliases": [
          "us",
          "usa",
          "united states",
          "america",
          "american"
        ]
      },
      {
        "key": "de",
        "label": "Germany",
        "aliases": [
          "de",
          "germany",
          "deutschland"
        ]
      },
      {
        "key": "it",
        "label": "Italy",
        "aliases": [
          "it",
          "italy",
          "italia"
        ]
      },
      {
        "key": "pt",
        "label": "Portugal",
        "aliases": [
          "pt",
          "portugal"
        ]
      },
      {
        "key": "tr",
        "label": "Turkey",
        "aliases": [
          "tr",
          "turkey",
          "turkiye"
        ]
      },
      {
        "key": "pl",
        "label": "Poland",
        "aliases": [
          "pl",
          "poland",
          "polska"
        ]
      },
      {
        "key": "ro",
        "label": "Romania",
        "aliases": [
          "ro",
          "romania"
        ]
      },
      {
        "key": "al",
        "label": "Albania",
        "aliases": [
          "al",
          "albania"
        ]
      },
      {
        "key": "gr",
        "label": "Greece",
        "aliases": [
          "gr",
          "greece",
          "hellas"
        ]
      },
      {
        "key": "nl",
        "label": "Netherlands",
        "aliases": [
          "nl",
          "netherlands",
          "holland",
          "nederland"
        ]
      },
      {
        "key": "se",
        "label": "Sweden",
        "aliases": [
          "se",
          "sweden",
          "sverige"
        ]
      },
      {
        "key": "no",
        "label": "Norway",
        "aliases": [
          "no",
          "norway",
          "norge"
        ]
      },
      {
        "key": "dk",
        "label": "Denmark",
        "aliases": [
          "dk",
          "denmark",
          "danmark"
        ]
      },
      {
        "key": "fi",
        "label": "Finland",
        "aliases": [
          "fi",
          "finland",
          "suomi"
        ]
      },
      {
        "key": "cz",
        "label": "Czechia",
        "aliases": [
          "cz",
          "czechia",
          "czech republic"
        ]
      },
      {
        "key": "hu",
        "label": "Hungary",
        "aliases": [
          "hu",
          "hungary",
          "magyarorszag"
        ]
      },
      {
        "key": "bg",
        "label": "Bulgaria",
        "aliases": [
          "bg",
          "bulgaria"
        ]
      },
      {
        "key": "hr",
        "label": "Croatia",
        "aliases": [
          "hr",
          "croatia",
          "hrvatska"
        ]
      },
      {
        "key": "rs",
        "label": "Serbia",
        "aliases": [
          "rs",
          "serbia",
          "srbija"
        ]
      },
      {
        "key": "sk",
        "label": "Slovakia",
        "aliases": [
          "sk",
          "slovakia",
          "slovensko"
        ]
      },
      {
        "key": "si",
        "label": "Slovenia",
        "aliases": [
          "si",
          "slovenia",
          "slovenija"
        ]
      },
      {
        "key": "ua",
        "label": "Ukraine",
        "aliases": [
          "ua",
          "ukraine",
          "ukraina"
        ]
      },
      {
        "key": "ru",
        "label": "Russia",
        "aliases": [
          "ru",
          "russia",
          "rossiya"
        ]
      },
      {
        "key": "in",
        "label": "India",
        "aliases": [
          "in",
          "india"
        ]
      },
      {
        "key": "pk",
        "label": "Pakistan",
        "aliases": [
          "pk",
          "pakistan"
        ]
      },
      {
        "key": "ir",
        "label": "Iran",
        "aliases": [
          "ir",
          "iran"
        ]
      },
      {
        "key": "il",
        "label": "Israel",
        "aliases": [
          "il",
          "israel"
        ]
      },
      {
        "key": "ca",
        "label": "Canada",
        "aliases": [
          "ca",
          "canada"
        ]
      },
      {
        "key": "au",
        "label": "Australia",
        "aliases": [
          "au",
          "australia"
        ]
      },
      {
        "key": "mx",
        "label": "Mexico",
        "aliases": [
          "mx",
          "mexico"
        ]
      },
      {
        "key": "br",
        "label": "Brazil",
        "aliases": [
          "br",
          "brazil",
          "brasil"
        ]
      },
      {
        "key": "ar",
        "label": "Argentina",
        "aliases": [
          "argentina"
        ]
      },
      {
        "key": "mena",
        "label": "MENA",
        "aliases": [
          "mena",
          "middle east",
          "arab world"
        ]
      },
      {
        "key": "balkans",
        "label": "Balkans",
        "aliases": [
          "balkan",
          "balkans",
          "ex yu"
        ]
      },
      {
        "key": "nordic",
        "label": "Nordic",
        "aliases": [
          "nordic"
        ]
      }
    ],
    "providers": [
      {
        "key": "vip",
        "label": "VIP",
        "aliases": [
          "vip"
        ]
      },
      {
        "key": "osn",
        "label": "OSN",
        "aliases": [
          "osn"
        ]
      },
      {
        "key": "mbc",
        "label": "MBC",
        "aliases": [
          "mbc"
        ]
      },
      {
        "key": "netflix",
        "label": "Netflix",
        "aliases": [
          "netflix"
        ]
      },
      {
        "key": "prime",
        "label": "Prime Video",
        "aliases": [
          "prime",
          "prime video",
          "amazon"
        ]
      },
      {
        "key": "disney",
        "label": "Disney+",
        "aliases": [
          "disney",
          "disney plus"
        ]
      },
      {
        "key": "shahid",
        "label": "Shahid",
        "aliases": [
          "shahid"
        ]
      },
      {
        "key": "bein",
        "label": "beIN",
        "aliases": [
          "bein",
          "bein sports"
        ]
      },
      {
        "key": "starzplay",
        "label": "STARZPLAY",
        "aliases": [
          "starzplay",
          "starz"
        ]
      },
      {
        "key": "appletv",
        "label": "Apple TV",
        "aliases": [
          "apple tv"
        ]
      },
      {
        "key": "hbo",
        "label": "HBO",
        "aliases": [
          "hbo"
        ]
      },
      {
        "key": "paramount",
        "label": "Paramount+",
        "aliases": [
          "paramount"
        ]
      }
    ]
  };
  // END GENERATED CATEGORY CLASSIFICATION

  var CATEGORY_ALL_FACET = '__all__';
  var CATEGORY_UNCLASSIFIED_FACET = '__none__';
  var CATEGORY_OTHER_GROUP = '__other__';
  // Two-letter language/region codes are useful on a provider prefix (`NO|`),
  // but the same letters are ordinary English words. Only trust them when a
  // `|` delimits them; full-word aliases remain available for bare names.
  var CATEGORY_AMBIGUOUS_SHORT_ALIASES = {
    no:1, it:1, in:1, id:1, hi:1, he:1, us:1, is:1, me:1, my:1,
    am:1, an:1, as:1, at:1, be:1, by:1, do:1, go:1, if:1, of:1,
    on:1, or:1, up:1, we:1
  };
  var classificationCache = Object.create(null);
  var classificationCacheOrder = [];

  function normalizeCategoryText(value) {
    var text = String(value || '');
    try { text = text.normalize('NFD').replace(/[\u0300-\u036f]/g, ''); } catch (_) { /* older runtime */ }
    return text.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  }

  function categoryTokens(value) {
    var normalized = normalizeCategoryText(value);
    return normalized ? normalized.split(' ') : [];
  }

  function categoryPrefix(name) {
    var text = String(name || '').trim();
    var separator = text.indexOf('|');
    if (separator <= 0 || separator >= text.length - 1) return '';
    if (!String(text.slice(separator + 1)).trim()) return '';
    var prefix = text.slice(0, separator).trim();
    if (!prefix || prefix.length > 16) return '';
    if (/[\u0000-\u001f\u007f]/.test(prefix)) return '';
    if (!/[0-9A-Za-z\u00c0-\uffff]/.test(prefix)) return '';
    return prefix;
  }

  function matchesTokensAt(tokens, start, aliasTokens) {
    if (!aliasTokens.length || start < 0 || start + aliasTokens.length > tokens.length) return false;
    for (var index = 0; index < aliasTokens.length; index += 1) {
      if (tokens[start + index] !== aliasTokens[index]) return false;
    }
    return true;
  }

  function collectRuleMatches(tokens, rules, atStart, allowAmbiguousShort) {
    var found = Object.create(null);
    (rules || []).forEach(function (rule, ruleIndex) {
      (rule.aliases || []).forEach(function (alias) {
        var aliasTokens = categoryTokens(alias);
        if (!aliasTokens.length) return;
        if (!allowAmbiguousShort && aliasTokens.length === 1 &&
          aliasTokens[0].length <= 2 && CATEGORY_AMBIGUOUS_SHORT_ALIASES[aliasTokens[0]]) return;
        var hit = false;
        if (atStart) {
          hit = matchesTokensAt(tokens, 0, aliasTokens);
        } else {
          for (var index = 0; index <= tokens.length - aliasTokens.length; index += 1) {
            if (matchesTokensAt(tokens, index, aliasTokens)) { hit = true; break; }
          }
        }
        if (!hit) return;
        var previous = found[rule.key];
        if (!previous || aliasTokens.length > previous.aliasTokens.length ||
          (aliasTokens.length === previous.aliasTokens.length && ruleIndex < previous.ruleIndex)) {
          found[rule.key] = { rule:rule, alias:alias, aliasTokens:aliasTokens, ruleIndex:ruleIndex };
        }
      });
    });
    return Object.keys(found).map(function (key) { return found[key]; }).sort(function (left, right) {
      if (left.aliasTokens.length !== right.aliasTokens.length) return right.aliasTokens.length - left.aliasTokens.length;
      return left.ruleIndex - right.ruleIndex;
    });
  }

  function bestMatch(matches) {
    return matches.length ? matches[0] : null;
  }

  function rawLeadingLabel(name, tokenCount) {
    var words = String(name || '').trim().split(/\s+/).slice(0, tokenCount);
    var label = words.join(' ').replace(/^[^0-9A-Za-z\u00c0-\uffff]+|[^0-9A-Za-z\u00c0-\uffff]+$/g, '').trim();
    return label;
  }

  function classificationSummary(value) {
    var labels = [];
    if (value.language) labels.push(value.language.label);
    if (value.quality) labels.push(value.quality.label);
    (value.formats || []).forEach(function (format) {
      if (labels.indexOf(format.label) < 0) labels.push(format.label);
    });
    if (value.provider) labels.push(value.provider.label);
    if (value.region) labels.push(value.region.label);
    if (value.ambiguousLanguage) labels.push('Multiple languages');
    return labels.length ? labels.join(' \u00b7 ') : 'Unclassified';
  }

  function classifyCategory(name) {
    var cacheKey = String(name || '');
    if (Object.prototype.hasOwnProperty.call(classificationCache, cacheKey)) return classificationCache[cacheKey];
    var prefix = categoryPrefix(cacheKey);
    var allTokens = categoryTokens(cacheKey);
    var prefixTokens = prefix ? categoryTokens(prefix) : null;
    var languageMatches = collectRuleMatches(
      prefixTokens || allTokens,
      CATEGORY_CLASSIFICATION_RULES.languages,
      false,
      !!prefixTokens
    );
    var language = languageMatches.length === 1 ? languageMatches[0].rule : null;
    var ambiguousLanguage = languageMatches.length > 1;
    var qualityMatches = collectRuleMatches(allTokens, CATEGORY_CLASSIFICATION_RULES.qualities, false, !!prefixTokens);
    qualityMatches.sort(function (left, right) {
      return Number(right.rule.rank || 0) - Number(left.rule.rank || 0);
    });
    var quality = bestMatch(qualityMatches);
    var formatMatches = collectRuleMatches(allTokens, CATEGORY_CLASSIFICATION_RULES.formats, false, !!prefixTokens);
    var region = bestMatch(collectRuleMatches(prefixTokens || allTokens, CATEGORY_CLASSIFICATION_RULES.regions, false, !!prefixTokens));
    var provider = bestMatch(collectRuleMatches(prefixTokens || allTokens, CATEGORY_CLASSIFICATION_RULES.providers, false, !!prefixTokens));
    var leadingRules = CATEGORY_CLASSIFICATION_RULES.languages
      .concat(CATEGORY_CLASSIFICATION_RULES.qualities)
      .concat(CATEGORY_CLASSIFICATION_RULES.formats)
      .concat(CATEGORY_CLASSIFICATION_RULES.regions)
      .concat(CATEGORY_CLASSIFICATION_RULES.providers);
    var leading = prefix ? null : bestMatch(collectRuleMatches(allTokens, leadingRules, true, false));
    var groupLabel = prefix || (leading ? rawLeadingLabel(cacheKey, leading.aliasTokens.length) : '');
    var groupKey = groupLabel ? groupLabel.toUpperCase() : CATEGORY_OTHER_GROUP;
    var markers = [];
    function marker(source, match) {
      if (!match) return;
      markers.push(source + ' ' + match.alias + ' \u2192 ' + match.rule.label);
    }
    if (prefixTokens) {
      marker('prefix', languageMatches[0]);
      marker('prefix', bestMatch(collectRuleMatches(prefixTokens, CATEGORY_CLASSIFICATION_RULES.qualities, false, true)));
      marker('prefix', region);
      marker('prefix', provider);
    } else {
      marker('name', languageMatches[0]);
      marker('name', bestMatch(qualityMatches));
      marker('name', provider);
      marker('name', region);
    }
    formatMatches.forEach(function (match) { marker('name', match); });
    if (ambiguousLanguage) {
      markers.push('ambiguous languages: ' + languageMatches.map(function (match) { return match.rule.label; }).join(', '));
    }
    var value = {
      prefix: prefix,
      groupKey: groupKey,
      groupLabel: groupLabel || 'Other',
      language: language ? { key:language.key, label:language.label } : null,
      quality: quality ? { key:quality.rule.key, label:quality.rule.label, rank:Number(quality.rule.rank || 0) } : null,
      region: region ? { key:region.rule.key, label:region.rule.label } : null,
      provider: provider ? { key:provider.rule.key, label:provider.rule.label } : null,
      formats: formatMatches.map(function (match) { return { key:match.rule.key, label:match.rule.label }; }),
      ambiguousLanguage: ambiguousLanguage,
      markers: markers
    };
    value.summary = classificationSummary(value);
    if (classificationCacheOrder.length >= 512) {
      var oldest = classificationCacheOrder.shift();
      if (oldest !== undefined) delete classificationCache[oldest];
    }
    classificationCache[cacheKey] = value;
    classificationCacheOrder.push(cacheKey);
    return value;
  }

  function categoryFacets(categories, dimension) {
    var counts = Object.create(null);
    var labels = Object.create(null);
    var unclassified = 0;
    (categories || []).forEach(function (category) {
      var name = String(category && category.category_name || '');
      var value = classifyCategory(name);
      var selected = dimension === 'quality' ? value.quality : value.language;
      if (!selected) { unclassified += 1; return; }
      counts[selected.key] = (counts[selected.key] || 0) + 1;
      labels[selected.key] = selected.label;
    });
    var rules = dimension === 'quality' ? CATEGORY_CLASSIFICATION_RULES.qualities : CATEGORY_CLASSIFICATION_RULES.languages;
    var options = rules.filter(function (rule) { return counts[rule.key]; }).map(function (rule) {
      return { key:rule.key, label:labels[rule.key] || rule.label, count:counts[rule.key], rank:Number(rule.rank || 0) };
    });
    options.sort(function (left, right) {
      if (dimension === 'quality') return right.rank - left.rank;
      return left.label < right.label ? -1 : (left.label > right.label ? 1 : 0);
    });
    if (unclassified) options.push({ key:CATEGORY_UNCLASSIFIED_FACET, label:'Unclassified', count:unclassified, rank:0 });
    return options;
  }

  function categoryMatchesFacet(name, dimension, key) {
    if (!key || key === CATEGORY_ALL_FACET) return true;
    var value = classifyCategory(name);
    var selected = dimension === 'quality' ? value.quality : value.language;
    return key === CATEGORY_UNCLASSIFIED_FACET ? !selected : !!selected && selected.key === key;
  }

  function categoryClassificationCacheInfo() {
    return { size: classificationCacheOrder.length, limit: 512 };
  }

  function trimSlash(value) {
    return String(value || '').trim().replace(/\/+$/, '');
  }

  function decodeMaybeBase64(value) {
    if (!value || typeof value !== 'string') return value || '';
    try {
      var decoded = atob(value);
      if (/^[\x09\x0A\x0D\x20-\x7E\u00A0-\uFFFF]*$/.test(decoded)) {
        try { return decodeURIComponent(escape(decoded)); } catch (error) { return decoded; }
      }
    } catch (error) { /* plain text */ }
    return value;
  }

  function safeExtension(value, fallback) {
    var extension = String(value || fallback || 'mp4').toLowerCase().replace(/[^a-z0-9]/g, '');
    return extension || fallback || 'mp4';
  }

  function objectValues(value) {
    return Object.keys(value || {}).map(function (key) { return value[key]; });
  }

  function normalizeCollection(data, preferredKeys) {
    if (Array.isArray(data)) return data;
    if (!data || typeof data !== 'object') return [];
    preferredKeys = preferredKeys || [];
    var keys = preferredKeys.concat(['data', 'results', 'items', 'streams', 'channels', 'movies', 'series', 'categories']);
    for (var index = 0; index < keys.length; index += 1) {
      if (Array.isArray(data[keys[index]])) return data[keys[index]];
    }
    return objectValues(data).filter(function (item) {
      return item && typeof item === 'object' && !Array.isArray(item);
    });
  }

  function deriveCategories(items, fallbackLabel) {
    var seen = {};
    var output = [];
    (items || []).forEach(function (item) {
      var id = item && (item.category_id !== undefined ? item.category_id : item.categoryId);
      if (id === undefined || id === null || id === '') return;
      var key = String(id);
      if (seen[key]) return;
      seen[key] = true;
      output.push({
        category_id: key,
        category_name: String(item.category_name || item.category || item.genre || (fallbackLabel + ' ' + key))
      });
    });
    return output;
  }

  function inferType(item) {
    if (!item) return 'live';
    if (item.content_type) return item.content_type;
    if (item.series_id !== undefined) return 'series';
    if (item.episode_num !== undefined || item.episode_id !== undefined) return 'episode';
    if (item.container_extension && item.stream_type !== 'live') return 'movie';
    return 'live';
  }

  function itemId(item, type) {
    type = type || inferType(item);
    if (!item) return '';
    if (type === 'series') return String(item.series_id !== undefined ? item.series_id : item.id || '');
    if (type === 'episode') return String(item.id !== undefined ? item.id : item.episode_id || item.stream_id || '');
    return String(item.stream_id !== undefined ? item.stream_id : item.id || '');
  }

  function hash32(value) {
    var hash = 2166136261;
    var text = String(value || '');
    for (var index = 0; index < text.length; index += 1) {
      hash ^= text.charCodeAt(index);
      hash = Math.imul ? Math.imul(hash, 16777619) : (hash * 16777619);
    }
    return (hash >>> 0).toString(16);
  }

  function parseAttributes(value) {
    var attributes = {};
    var text = String(value || '');
    var pattern = /([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"|([A-Za-z0-9_-]+)\s*=\s*'([^']*)'|([A-Za-z0-9_-]+)=([^\s]+)/g;
    var match;
    while ((match = pattern.exec(text))) {
      var key = match[1] || match[3] || match[5];
      var item = match[2] !== undefined ? match[2] : match[4] !== undefined ? match[4] : match[6];
      if (key) attributes[key.toLowerCase()] = item || '';
    }
    return attributes;
  }

  function mediaExtension(url) {
    try {
      var parsed = new URL(String(url));
      var match = parsed.pathname.toLowerCase().match(/\.([a-z0-9]{2,5})$/);
      return match ? match[1] : '';
    } catch (_) {
      var clean = String(url || '').split('?')[0].split('#')[0];
      var fallback = clean.toLowerCase().match(/\.([a-z0-9]{2,5})$/);
      return fallback ? fallback[1] : '';
    }
  }

  function m3uContentType(url, group) {
    var extension = mediaExtension(url);
    if (['mp4', 'mkv', 'avi', 'mov', 'wmv', 'flv', 'm4v', 'webm', 'mpg', 'mpeg'].indexOf(extension) >= 0) return 'movie';
    if (/\b(vod|movies?|films?|cinema)\b/i.test(String(group || ''))) return 'movie';
    return 'live';
  }

  function m3uCategory(group) {
    var name = String(group || '').trim() || 'Ungrouped';
    return { id: 'm3u-group-' + hash32(name.toLowerCase()), name: name };
  }

  function m3uTitleSeparator(value) {
    var quote = '';
    for (var index = 0; index < value.length; index += 1) {
      var character = value.charAt(index);
      if (quote) {
        if (character === quote) quote = '';
      } else if (character === '"' || character === "'") {
        quote = character;
      } else if (character === ',') {
        return index;
      }
    }
    return -1;
  }

  function parseM3u(text) {
    var source = String(text || '').replace(/^\uFEFF/, '');
    var lines = source.split(/\r?\n/);
    var entries = [];
    var warnings = [];
    var epgUrls = [];
    var pending = null;
    var pendingGroup = '';
    var sawHeader = false;
    var seen = {};

    function addEntry(pendingEntry, url) {
      url = String(url || '').trim();
      if (!url || /^#/.test(url)) return;
      var name = String(pendingEntry && (pendingEntry.name || pendingEntry.attributes['tvg-name']) || '').trim();
      if (!name) {
        try { name = decodeURIComponent((new URL(url)).pathname.split('/').filter(Boolean).pop() || 'Untitled channel'); }
        catch (_) { name = 'Untitled channel'; }
      }
      var attributes = pendingEntry && pendingEntry.attributes || {};
      var group = String(attributes['group-title'] || (pendingEntry && pendingEntry.group) || pendingGroup || '').trim();
      var category = m3uCategory(group);
      var type = m3uContentType(url, group);
      var identity = url + '\n' + name + '\n' + group;
      var id = 'm3u-' + hash32(identity);
      if (seen[id]) {
        warnings.push('Duplicate playlist entry skipped: ' + name);
        return;
      }
      seen[id] = true;
      var extension = mediaExtension(url);
      entries.push({
        stream_id: id,
        id: id,
        name: name,
        title: name,
        content_type: type,
        category_id: category.id,
        category_name: category.name,
        group_title: group,
        stream_url: url,
        stream_icon: String(attributes['tvg-logo'] || '').trim(),
        tvg_id: String(attributes['tvg-id'] || '').trim(),
        tvg_name: String(attributes['tvg-name'] || '').trim(),
        channel_number: String(attributes['tvg-chno'] || attributes['channel-number'] || '').trim(),
        container_extension: type === 'movie' && extension ? extension : null
      });
    }

    for (var index = 0; index < lines.length; index += 1) {
      var line = lines[index].trim();
      if (!line) continue;
      if (/^#EXTM3U(?:\s|$)/i.test(line)) {
        sawHeader = true;
        var header = parseAttributes(line.replace(/^#EXTM3U/i, ''));
        var epg = header['url-tvg'] || header['x-tvg-url'] || '';
        epg.split(',').map(function (value) { return value.trim(); }).filter(Boolean).forEach(function (value) {
          if (epgUrls.indexOf(value) < 0) epgUrls.push(value);
        });
        continue;
      }
      if (/^#EXTINF:/i.test(line)) {
        if (pending) warnings.push('Playlist entry without a URL skipped: ' + (pending.name || 'unknown'));
        var body = line.replace(/^#EXTINF:/i, '');
        var comma = m3uTitleSeparator(body);
        var metadata = comma >= 0 ? body.slice(0, comma) : body;
        var displayName = comma >= 0 ? body.slice(comma + 1).trim() : '';
        var durationText = metadata.match(/^\s*(-?\d+(?:\.\d+)?)/);
        pending = {
          name: displayName,
          duration: durationText ? Number(durationText[1]) : -1,
          attributes: parseAttributes(metadata),
          group: ''
        };
        continue;
      }
      if (/^#EXTGRP:/i.test(line)) {
        pendingGroup = line.slice(8).trim();
        if (pending) pending.group = pendingGroup;
        continue;
      }
      if (/^#EXT-X-|^#EXTM3U|^#PLAYLIST:|^#EXTVLCOPT:|^#KODIPROP:/i.test(line)) continue;
      if (/^#/.test(line)) continue;
      if (pending) {
        var completed = pending;
        completed.group = completed.group || pendingGroup;
        addEntry(completed, line);
        pending = null;
        pendingGroup = '';
      } else {
        addEntry(null, line);
      }
    }
    if (pending) warnings.push('Playlist entry without a URL skipped: ' + (pending.name || 'unknown'));
    if (!sawHeader && lines.length) warnings.push('Playlist is missing the #EXTM3U header.');
    return { entries: entries, categories: m3uCategories(entries), epgUrls: epgUrls, warnings: warnings };
  }

  function m3uCategories(entries, type) {
    var seen = {};
    var result = [];
    (entries || []).forEach(function (entry) {
      if (!entry || (type && entry.content_type !== type)) return;
      var id = String(entry.category_id || '');
      if (!id || seen[id]) return;
      seen[id] = true;
      result.push({ category_id: id, category_name: String(entry.category_name || 'Ungrouped') });
    });
    return result;
  }

  function normalizeAlternateHost(value) {
    var host = String(value || '').trim().replace(/\/+$/, '');
    if (!host) return '';
    if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(host)) host = 'https://' + host;
    try {
      var parsed = new URL(host);
      return parsed.origin;
    } catch (_) { return ''; }
  }

  function rewriteM3uHost(url, host) {
    var alternative = normalizeAlternateHost(host);
    if (!alternative) return '';
    try {
      var original = new URL(String(url));
      var replacement = new URL(alternative);
      return replacement.protocol + '//' + replacement.host + original.pathname + original.search + original.hash;
    } catch (_) { return ''; }
  }

  function mimeForUrl(url) {
    var extension = mediaExtension(url);
    if (extension === 'm3u8') return 'application/x-mpegURL';
    if (extension === 'ts') return 'video/mp2t';
    if (extension === 'mp4' || extension === 'm4v') return 'video/mp4';
    if (extension === 'webm') return 'video/webm';
    return '';
  }

  function m3uCandidates(item, alternateHosts) {
    if (!item) return [];
    var url = String(item.stream_url || item.streamUrl || item.url || '').trim();
    if (!url) return [];
    var candidates = [{ url: url, label: item.content_type === 'movie' ? 'Direct file' : 'Playlist stream', format: mediaExtension(url) || 'direct', mime: mimeForUrl(url) }];
    var seen = {};
    seen[url] = true;
    (alternateHosts || []).forEach(function (host, index) {
      var rewritten = rewriteM3uHost(url, host);
      if (!rewritten || seen[rewritten]) return;
      seen[rewritten] = true;
      candidates.push({ url: rewritten, label: 'Alternate host ' + (index + 1), format: mediaExtension(rewritten) || 'direct', mime: mimeForUrl(rewritten) });
    });
    return candidates;
  }

  function recoveryDecision(state, failure) {
    state = state || {};
    failure = failure || {};
    var kind = String(failure.kind || 'transient');
    var permanent = ['auth', 'forbidden', 'not_found', 'unsupported'].indexOf(kind) >= 0;
    var candidateIndex = Number(state.candidateIndex || 0);
    var candidateCount = Number(state.candidateCount || 0);
    var attempts = Number(state.attempts || 0);
    var maxAttempts = Math.max(1, Number(state.maxAttempts || 2));
    if (!permanent && state.retryBeforeNext && attempts < maxAttempts) {
      return { action: 'retry', reason: 'transient', delayMs: Math.min(250 * Math.pow(2, attempts), 2000) };
    }
    if (candidateIndex + 1 < candidateCount) {
      return { action: 'next', reason: 'candidate', delayMs: permanent ? 0 : Math.min(250 * Math.pow(2, attempts), 2000) };
    }
    if (!permanent && attempts < maxAttempts) {
      return { action: 'retry', reason: 'transient', delayMs: Math.min(250 * Math.pow(2, attempts), 2000) };
    }
    return { action: 'fail', reason: permanent ? 'permanent' : 'exhausted' };
  }

  function buildStreamUrl(credentials, type, streamId, extension) {
    var server = trimSlash(credentials && credentials.server);
    var username = encodeURIComponent(String(credentials && credentials.username || '').trim());
    var password = encodeURIComponent(String(credentials && credentials.password || ''));
    var segment = type === 'episode' ? 'series' : type === 'movie' ? 'movie' : 'live';
    return server + '/' + segment + '/' + username + '/' + password + '/' + streamId + '.' + safeExtension(extension, type === 'live' ? 'm3u8' : 'mp4');
  }

  return {
    providerKey: function (credentials) {
      if (!credentials) return '';
      var playlistUrl = String(credentials.playlistUrl || credentials.playlist_url || '').trim();
      if (credentials.sourceType === 'm3u' || credentials.source_type === 'm3u' || playlistUrl) {
        try { var playlist = new URL(playlistUrl); playlist.hash = ''; playlistUrl = playlist.toString(); } catch (_) {}
        return JSON.stringify(['m3u', playlistUrl]);
      }
      var server = trimSlash(credentials.server);
      try { var url = new URL(server); url.hostname = url.hostname.toLowerCase(); server = trimSlash(url.toString()); } catch (_) {}
      return JSON.stringify([server, String(credentials.username || '').trim()]);
    },
    trimSlash: trimSlash,
    decodeMaybeBase64: decodeMaybeBase64,
    safeExtension: safeExtension,
    normalizeCollection: normalizeCollection,
    deriveCategories: deriveCategories,
    inferType: inferType,
    itemId: itemId,
    buildStreamUrl: buildStreamUrl,
    hash32: hash32,
    parseAttributes: parseAttributes,
    mediaExtension: mediaExtension,
    m3uContentType: m3uContentType,
    parseM3u: parseM3u,
    m3uCategories: m3uCategories,
    rewriteM3uHost: rewriteM3uHost,
    m3uCandidates: m3uCandidates,
    recoveryDecision: recoveryDecision,
    normalizeCategoryText: normalizeCategoryText,
    categoryPrefix: categoryPrefix,
    classifyCategory: classifyCategory,
    categoryFacets: categoryFacets,
    categoryMatchesFacet: categoryMatchesFacet,
    categoryClassificationCacheInfo: categoryClassificationCacheInfo,
    CATEGORY_ALL_FACET: CATEGORY_ALL_FACET,
    CATEGORY_UNCLASSIFIED_FACET: CATEGORY_UNCLASSIFIED_FACET,
    CATEGORY_OTHER_GROUP: CATEGORY_OTHER_GROUP
  };
}));
