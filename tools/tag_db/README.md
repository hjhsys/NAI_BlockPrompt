# Bundled Korean tag translations

`tag_translations_ko.jsonl.gzip` is a generated Android asset. Regenerate it only
from a reviewed translation JSONL and the canonical bundled tag CSV:

```powershell
python tools/tag_db/build_bundled_translations.py `
  --canonical app/src/main/assets/danbooru_tags_pt20.csv `
  --translations "<path-to>/translated_all_checked.jsonl" `
  --output app/src/main/assets/tag_translations_ko.jsonl.gzip
```

The generator rejects duplicate tags, empty Korean translations, unsupported
app categories, and changes to canonical source category or post count. Rows not
present in the canonical CSV are accepted only when both canonical metadata
fields are null.

The app imports this asset only when `BundledTagImporter.BUNDLED_VERSION`
increases. Import updates base tag enrichment and `base_translations`; it does
not replace the Room database or write to user override/usage tables.

## Incremental reviewed update

Use the current gzip asset as `--base` and pass only reviewed changes as
`--translations`. Incremental `translated` rows must include
`translation_source` and `verification_status` (`official`, `structured`,
`cross_checked`, `reviewed`, `unverified`, or `unresolved`). Optional
`confidence` must be between 0 and 1. Unverified Character/Copyright names must
remain `needs_review=true` and are never promoted as verified names.

```powershell
python tools/tag_db/build_bundled_translations.py `
  --canonical app/src/main/assets/danbooru_tags_pt20.csv `
  --base app/src/main/assets/tag_translations_ko.jsonl.gzip `
  --translations "<path-to>/reviewed_updates.jsonl" `
  --review-output "<work-dir>/needs_review.jsonl" `
  --exclusions-output "<work-dir>/exclusion_candidates.jsonl" `
  --output "<work-dir>/tag_translations_ko.jsonl.gzip"
```

The tool keeps untouched Base rows byte-for-data equivalent, rejects unknown or
renamed canonical tags, duplicate updates, source metadata changes, unsupported
app categories, and incomplete provenance. `review` and `excluded_candidate`
rows are separated from the Base output. Exclusion output uses the 29-stage
AI-origin reason schema. Review these artifacts before importing or replacing
the generated asset. Increase `BUNDLED_VERSION` only after reviewing and
copying the output asset; Room then updates Base rows while preserving User
Overrides, favorites, thumbnails, exclusions, usage data, and user-created tags.

The canonical CSV provenance is documented in `THIRD_PARTY_NOTICES.md`.
Classified category datasets are hints only; do not treat them as Korean
translations. Do not bundle community translation data until its redistribution
license has been verified.
