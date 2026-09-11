# Third-party data notices

## Danbooru tag list

`app/src/main/assets/danbooru_tags_pt20.csv` is sourced from
[DraconicDragon/dbr-e621-lists-archive](https://github.com/DraconicDragon/dbr-e621-lists-archive),
file `danbooru_2026-04-01_pt20-ia-dd.csv`.

The source repository is released under [The Unlicense](https://github.com/DraconicDragon/dbr-e621-lists-archive/blob/main/LICENSE).
Only the Danbooru-specific list is bundled; merged e621 data is not included.

The list supplies canonical tags, Danbooru category numbers, post counts, and English aliases.
It does not supply Korean translations.

## Korean translation layer

`app/src/main/assets/tag_translations_ko.jsonl.gzip` is a separately generated
translation/enrichment asset, based on the project owner's AI-assisted reviewed
`translated_all_checked.jsonl`. It is not a Korean dataset supplied by the
upstream tag-list repository. No third-party Korean translation dataset is
identified as its source.

The layer contains Korean names, aliases, app categories, suggested categories,
and review flags matched to canonical tags. AI-assisted translations may contain
errors; users can maintain local overrides. See
[the generation tool documentation](tools/tag_db/README.md) for validation and
rebuilding. The upstream Unlicense notice above describes the canonical source
list, not the provenance of the Korean translations.
