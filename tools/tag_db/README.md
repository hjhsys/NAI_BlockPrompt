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
