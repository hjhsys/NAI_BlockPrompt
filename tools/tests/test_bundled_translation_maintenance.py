import gzip
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/tag_db/build_bundled_translations.py"
SPEC = importlib.util.spec_from_file_location("translation_builder", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BundledTranslationMaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.canonical = self.root / "canonical.csv"
        self.canonical.write_text("smile,0,10,\nalice_(series),4,5,\n", encoding="utf-8")
        self.base = self.root / "base.jsonl.gzip"
        base_rows = [
            {"tag": "smile", "ko": "미소", "aliases_ko": [], "app_category": "expression", "source_category": "general", "post_count": 10},
            {"tag": "alice_(series)", "ko": "앨리스", "aliases_ko": [], "app_category": "character", "source_category": "character", "post_count": 5},
        ]
        with gzip.open(self.base, "wt", encoding="utf-8") as output:
            for row in base_rows:
                output.write(json.dumps(row, ensure_ascii=False) + "\n")

    def write_updates(self, *rows):
        path = self.root / "updates.jsonl"
        path.write_text("\n".join(json.dumps(row, ensure_ascii=False) for row in rows) + "\n", encoding="utf-8")
        return path

    def test_incremental_merge_preserves_untouched_base_and_splits_candidates(self):
        updates = self.write_updates(
            {"tag": "smile", "status": "translated", "ko": "웃는 표정", "aliases_ko": ["미소"], "app_category": "expression", "source_category": "general", "post_count": 10, "translation_source": "manual-review", "verification_status": "reviewed", "confidence": 1.0},
            {"tag": "alice_(series)", "status": "excluded_candidate", "reason_code": "invalid", "reason_text": "테스트 후보"},
        )
        output, review, exclusions = self.root / "out.gzip", self.root / "review.jsonl", self.root / "exclude.jsonl"
        summary = MODULE.build(self.canonical, updates, output, self.base, review, exclusions)
        rows = MODULE.read_jsonl(output)
        self.assertEqual(2, len(rows))
        self.assertEqual("웃는 표정", next(row for row in rows if row["tag"] == "smile")["ko"])
        self.assertEqual("앨리스", next(row for row in rows if row["tag"] == "alice_(series)")["ko"])
        self.assertEqual("AI", MODULE.read_jsonl(exclusions)[0]["origin"])
        self.assertEqual(1, summary["exclusion_rows"])

    def test_unknown_canonical_and_unverified_proper_name_are_rejected(self):
        unknown = self.write_updates({"tag": "renamed", "status": "translated", "ko": "오류", "app_category": "other", "translation_source": "ai", "verification_status": "unverified"})
        with self.assertRaisesRegex(ValueError, "unknown canonical"):
            MODULE.build(self.canonical, unknown, self.root / "out.gzip", self.base, self.root / "review", self.root / "exclude")

        unverified = self.write_updates({"tag": "alice_(series)", "status": "translated", "ko": "앨리스", "aliases_ko": [], "app_category": "character", "source_category": "character", "post_count": 5, "translation_source": "ai", "verification_status": "unverified", "needs_review": False})
        with self.assertRaisesRegex(ValueError, "proper name"):
            MODULE.build(self.canonical, unverified, self.root / "out.gzip", self.base, self.root / "review", self.root / "exclude")


if __name__ == "__main__":
    unittest.main()
