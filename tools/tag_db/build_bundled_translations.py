#!/usr/bin/env python3
"""Validate, merge, and build the bundled Korean translation asset."""

import argparse
import csv
import gzip
import json
from pathlib import Path

EXPECTED_LEGACY_COUNT = 201_273
ALLOWED_CATEGORIES = {
    "", "general", "artist", "copyright", "character", "meta",
    "clothes", "pose", "hair", "body", "expression", "accessory",
    "background", "composition", "lighting", "effect", "other",
}
ALLOWED_VERIFICATION = {
    "official", "structured", "cross_checked", "reviewed", "unverified", "unresolved",
}
ALLOWED_EXCLUSION_REASONS = {"typo", "invalid", "noise"}
CATEGORY_NAMES = {"0": "general", "1": "artist", "3": "copyright", "4": "character", "5": "meta"}


def open_text(path: Path):
    return gzip.open(path, "rt", encoding="utf-8-sig") if path.suffix in {".gz", ".gzip"} else path.open(encoding="utf-8-sig")


def read_jsonl(path: Path) -> list[dict]:
    rows = []
    with open_text(path) as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip() or line.lstrip().startswith("```"):
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as error:
                raise ValueError(f"invalid JSON at {path}:{line_number}: {error.msg}") from error
            if row.get("type"):
                continue
            if not isinstance(row, dict) or not str(row.get("tag", "")).strip():
                raise ValueError(f"missing canonical tag at {path}:{line_number}")
            rows.append(row)
    return rows


def write_jsonl(path: Path | None, rows: list[dict]) -> None:
    if path is None:
        if rows:
            raise ValueError("review/exclusion rows exist; provide the matching output path")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")


def canonical_source(path: Path) -> dict[str, tuple[str | None, int]]:
    with path.open(encoding="utf-8-sig", newline="") as source:
        return {
            row[0].strip(): (CATEGORY_NAMES.get(row[1], row[1] or None), int(row[2]))
            for row in csv.reader(source) if len(row) >= 3 and row[0].strip()
        }


def validate_translation(row: dict, original: tuple[str | None, int] | None, incremental: bool) -> dict:
    tag = str(row["tag"]).strip()
    if not str(row.get("ko", "")).strip():
        raise ValueError(f"empty ko: {tag}")
    if row.get("app_category", "") not in ALLOWED_CATEGORIES:
        raise ValueError(f"unsupported app_category for {tag}: {row.get('app_category')}")
    if original is not None:
        if row.get("source_category") != original[0] or row.get("post_count") != original[1]:
            raise ValueError(f"canonical metadata mismatch: {tag}")
    elif row.get("source_category") is not None or row.get("post_count") is not None:
        raise ValueError(f"unknown tag has invented canonical metadata: {tag}")

    source = str(row.get("translation_source", "")).strip()
    verification = str(row.get("verification_status", "")).strip()
    if incremental and (not source or verification not in ALLOWED_VERIFICATION):
        raise ValueError(f"incremental update requires translation_source and verification_status: {tag}")
    if verification and verification not in ALLOWED_VERIFICATION:
        raise ValueError(f"unsupported verification_status for {tag}: {verification}")
    confidence = row.get("confidence")
    if confidence is not None and (not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1):
        raise ValueError(f"confidence must be between 0 and 1: {tag}")
    if row.get("source_category") in {"character", "copyright"} and verification in {"unverified", "unresolved"} and not row.get("needs_review", False):
        raise ValueError(f"unverified proper name must remain needs_review: {tag}")
    return {**row, "tag": tag}


def build(canonical_path: Path, updates_path: Path, output_path: Path, base_path: Path | None,
          review_output: Path | None, exclusions_output: Path | None) -> dict:
    canonical = canonical_source(canonical_path)
    base_rows = read_jsonl(base_path) if base_path else []
    merged = {row["tag"]: row for row in base_rows}
    if len(merged) != len(base_rows):
        raise ValueError("duplicate canonical tag in base input")

    seen_updates = set()
    reviews, exclusions = [], []
    for row in read_jsonl(updates_path):
        tag = str(row["tag"]).strip()
        if tag in seen_updates:
            raise ValueError(f"duplicate tag in updates: {tag}")
        seen_updates.add(tag)
        if tag not in canonical and tag not in merged:
            raise ValueError(f"unknown canonical tag: {tag}")
        status = row.get("status", "translated")
        if status == "excluded_candidate":
            if row.get("reason_code") not in ALLOWED_EXCLUSION_REASONS or not str(row.get("reason_text", "")).strip():
                raise ValueError(f"invalid exclusion candidate: {tag}")
            exclusions.append({
                "tag": tag, "origin": "AI", "reason_code": row["reason_code"],
                "reason_text": row["reason_text"], "user_confirmed": False,
            })
        elif status == "review":
            reviews.append(row)
        elif status == "unchanged":
            if tag not in merged:
                raise ValueError(f"unchanged tag is absent from base: {tag}")
        elif status == "translated":
            merged[tag] = validate_translation(row, canonical.get(tag), incremental=base_path is not None)
        else:
            raise ValueError(f"unsupported status for {tag}: {status}")

    missing = set(canonical) - set(merged)
    if missing:
        raise ValueError(f"translations missing {len(missing)} canonical tags; first={sorted(missing)[0]}")
    if base_path is None and len(merged) != EXPECTED_LEGACY_COUNT:
        raise ValueError(f"expected {EXPECTED_LEGACY_COUNT} rows, got {len(merged)}")

    write_jsonl(review_output, reviews)
    write_jsonl(exclusions_output, exclusions)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(output_path, "wt", encoding="utf-8", newline="\n", compresslevel=9) as output:
        for tag in sorted(merged):
            output.write(json.dumps(merged[tag], ensure_ascii=False, separators=(",", ":")) + "\n")
    return {
        "canonical_input": len(canonical), "base_rows": len(base_rows), "update_rows": len(seen_updates),
        "output_rows": len(merged), "review_rows": len(reviews), "exclusion_rows": len(exclusions),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonical", type=Path, required=True)
    parser.add_argument("--translations", type=Path, required=True, help="Reviewed full input or incremental update JSONL")
    parser.add_argument("--base", type=Path, help="Existing generated JSONL/gzip for an incremental merge")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--review-output", type=Path)
    parser.add_argument("--exclusions-output", type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.canonical, args.translations, args.output, args.base, args.review_output, args.exclusions_output), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
