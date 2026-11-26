# Romanize (`utils/romanize`)

## Overview
Offline rule- and lexicon-based romanization subsystem (`com.drliki.MusicLi.utils.romanize`) supporting Indic scripts (`DEVANAGARI`, `GURMUKHI`, `TAMIL`, `TELUGU`, `KANNADA`, `MALAYALAM`) with Indo-Aryan schwa deletion, plus Perso-Arabic `URDU` transliteration.

## Interface
- [`IndicScript`](IndicScript.kt):
  - `[IndicScript.kt:IndicScript]` enum (`DEVANAGARI`, `GURMUKHI`, `TAMIL`, `TELUGU`, `KANNADA`, `MALAYALAM`, `URDU`) with `unicodeRange`, `usesSchwaDeletion`, and `extraRanges`; methods `[IndicScript.kt:IndicScript.matches]`, `[IndicScript.kt:IndicScript.Companion.fromCodePoint]`, and `[IndicScript.kt:IndicScript.Companion.containsAny]`.
  - `[IndicScript.kt:RomanizationStyle]` enum (`SING_ALONG`, `COMPACT`) with `storageValue` and `[IndicScript.kt:RomanizationStyle.Companion.fromStorage]`.
- [`IndicRomanizer`](IndicRomanizer.kt): `[IndicRomanizer.kt:IndicRomanizer]` singleton exposing `[IndicRomanizer.kt:IndicRomanizer.containsIndicScript]` and `[IndicRomanizer.kt:IndicRomanizer.romanize]`.
- [`UrduRomanizer`](UrduRomanizer.kt): `[UrduRomanizer.kt:UrduRomanizer]` internal singleton exposing `[UrduRomanizer.kt:UrduRomanizer.romanizeWord]`.

## Invariants
- `[IndicScript.kt:RomanizationStyle.Companion.fromStorage]` defaults unknown or `null` storage values to `RomanizationStyle.SING_ALONG`.
- `[IndicScript.kt:IndicScript]` enables `usesSchwaDeletion = true` only for `DEVANAGARI` and `GURMUKHI`; `TAMIL`, `TELUGU`, `KANNADA`, `MALAYALAM`, and `URDU` keep `usesSchwaDeletion = false`.
- `[IndicRomanizer.kt:IndicRomanizer.romanize]` returns blank strings unchanged, copies non-Indic code points verbatim, and enables Marathi conjunct mapping (`"ज्ञ"` -> `"dny"` vs `"gy"`) across a line when `'\u0933'`, `'\u0972'`, or `'\u0911'` appears.
- `[IndicRomanizer.kt:IndicRomanizer.romanizeToken]` delegates `IndicScript.URDU` tokens to `[UrduRomanizer.kt:UrduRomanizer.romanizeWord]` and returns `""` when no syllable units are parsed.
- `[IndicRomanizer.kt:IndicRomanizer.applyIndoAryanSchwaDeletion]` preserves inherent `'a'` on single-syllable words (`units.size <= 1`) and word-initial syllables (`idx == 0`), and skips medial schwa deletion on postmodifiers or adjacent vowelless/deleted syllables to avoid triple consonant clusters.
- `[IndicRomanizer.kt:IndicRomanizer.formatVowel]` and `[UrduRomanizer.kt:UrduRomanizer.applyStyle]` preserve long vowels (`"aa"`, `"ee"`, `"oo"`) under `SING_ALONG` and collapse `"aa"` -> `"a"`, `"ee"` -> `"i"`, and `"oo"` -> `"u"` under `COMPACT`.
- `[UrduRomanizer.kt:UrduRomanizer.romanizeWord]` returns empty tokens immediately, checks `commonWords` on variant-normalized input before diacritic/tatweel-stripped input, and falls back to rule-based transliteration when absent.

## Side Effects
- None.

## Verification
- Run: `./gradlew :app:testDebugUnitTest`
