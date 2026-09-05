#!/usr/bin/env python3
"""Validate canonical tag data and build the one-time bundled translation asset."""

import argparse
import csv
import gzip
import json
from pathlib import Path

EXPECTED_COUNT = 201_273
ALLOWED_CATEGORIES = {
    "", "general", "artist", "copyright", "character", "meta",
    "clothes", "pose", "hair", "body", "expression", "accessory",
    "background", "composition", "lighting", "effect", "other",
}
CATEGORY_NAMES = {"0": "general", "1": "artist", "3": "copyright", "4": "character", "5": "meta"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--translations", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    with args.canonical.open(encoding="utf-8-sig", newline="") as source:
        canonical = {row[0].strip(): (CATEGORY_NAMES.get(row[1]), int(row[2])) for row in csv.reader(source) if len(row) >= 3}

    rows = []
    seen = set()
    with args.translations.open(encoding="utf-8-sig") as source:
        for line_number, line in enumerate(source, 1):
            row = json.loads(line)
            tag = row["tag"]
            if tag in seen:
                raise ValueError(f"duplicate tag at line {line_number}: {tag}")
            seen.add(tag)
            if not str(row.get("ko", "")).strip():
                raise ValueError(f"empty ko at line {line_number}: {tag}")
            if row.get("app_category", "") not in ALLOWED_CATEGORIES:
                raise ValueError(f"unsupported app_category at line {line_number}: {row.get('app_category')}")
            original = canonical.get(tag)
            if original is not None:
                if row.get("source_category") != original[0] or row.get("post_count") != original[1]:
                    raise ValueError(f"canonical metadata mismatch at line {line_number}: {tag}")
            elif row.get("source_category") is not None or row.get("post_count") is not None:
                raise ValueError(f"unknown tag has invented canonical metadata at line {line_number}: {tag}")
            rows.append(row)

    if len(rows) != EXPECTED_COUNT:
        raise ValueError(f"expected {EXPECTED_COUNT} rows, got {len(rows)}")
    missing = set(canonical) - seen
    if missing:
        raise ValueError(f"translations missing {len(missing)} canonical tags; first={sorted(missing)[0]}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(args.output, "wt", encoding="utf-8", newline="\n", compresslevel=9) as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")

    print(json.dumps({
        "canonical_input": len(canonical),
        "translation_rows": len(rows),
        "additional_null_metadata_tags": len(seen - set(canonical)),
        "duplicates": len(rows) - len(seen),
        "empty_ko": sum(not str(row.get("ko", "")).strip() for row in rows),
        "needs_review": sum(row.get("needs_review") is True for row in rows),
        "suggested_category": sum(bool(row.get("suggested_category")) for row in rows),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
