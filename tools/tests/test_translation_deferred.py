"""Host SQLite regression checks against the actual Room schema and DAO SQL."""
import json
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAVA = ROOT / 'app/src/main/java/com/hjhsys/naiblockprompt'
SCHEMAS = ROOT / 'app/schemas/com.hjhsys.naiblockprompt.data.local.AppDatabase'


class DeferredTranslationTest(unittest.TestCase):
    def test_source_category_migration_preserves_other_fields(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        db.execute('CREATE TABLE tags (id TEXT, danbooruCategory TEXT, appCategory TEXT, useCount INTEGER)')
        values = ['0', '1', '3', '4', '5', 'unknown', None]
        db.executemany('INSERT INTO tags VALUES (?, ?, ?, ?)',
                       [(str(i), value, 'custom', 17) for i, value in enumerate(values)])
        db.execute('CREATE TABLE user_tag_overrides (tagId TEXT, korean TEXT, favorite INTEGER)')
        db.execute("INSERT INTO user_tag_overrides VALUES ('0', '사용자 번역', 1)")
        source = (JAVA / 'NaiBlockPromptApplication.kt').read_text(encoding='utf-8')
        migration = re.search(r'UPDATE tags SET danbooruCategory[^"\n]+', source).group()
        db.execute(migration)
        self.assertEqual(['general', 'artist', 'copyright', 'character', 'meta', 'unknown', None],
                         [row[0] for row in db.execute('SELECT danbooruCategory FROM tags ORDER BY id')])
        self.assertEqual([('custom', 17)] * 7, list(db.execute('SELECT appCategory, useCount FROM tags')))
        self.assertEqual(('0', '사용자 번역', 1), db.execute('SELECT * FROM user_tag_overrides').fetchone())
        db.execute(migration)
        self.assertEqual(7, db.execute('SELECT COUNT(*) FROM tags').fetchone()[0])

    def test_migration_queue_and_protected_deletion(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        schema = json.loads((SCHEMAS / '10.json').read_text(encoding='utf-8'))
        for entity in schema['database']['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))

        def insert(table, **values):
            for _, name, kind, required, default, _ in db.execute(f'PRAGMA table_info({table})'):
                if name not in values and required and default is None:
                    values[name] = '' if kind == 'TEXT' else 0
            db.execute(f'INSERT INTO {table} ({",".join(values)}) VALUES ({",".join("?" for _ in values)})', list(values.values()))

        for tag in ('review', 'todo', 'protected'):
            insert('tags', id=tag, canonicalTag=tag, danbooruCategory='general')
        insert('user_tag_overrides', id='personal', tagId='protected', korean='keep', favorite=1)
        source = (JAVA / 'NaiBlockPromptApplication.kt').read_text(encoding='utf-8')
        migration = re.search(r'ALTER TABLE user_tag_overrides ADD COLUMN translationDeferred[^"\n]+', source).group()
        db.execute(migration)
        self.assertEqual(('keep', 1, 0), db.execute('SELECT korean, favorite, translationDeferred FROM user_tag_overrides').fetchone())
        insert('user_tag_overrides', id='review-state', tagId='review', translationDeferred=1)

        dao = (JAVA / 'data/local/dao/AppDaos.kt').read_text(encoding='utf-8')
        queries = {name: multi or single for multi, single, name in re.findall(r'@Query\((?:"""(.*?)"""|"([^"\n]*)")\)\s*(?:suspend\s+)?fun\s+(\w+)', dao, re.S)}
        self.assertEqual(1, db.execute(queries['observeMissingTranslationCount']).fetchone()[0])
        params = dict(limit=1000, missingTranslation=1, missingCategory=1, includeDeferred=0)
        self.assertEqual(['todo'], [row[1] for row in db.execute(queries['translationCandidates'], params)])
        params['includeDeferred'] = 1
        self.assertEqual({'review', 'todo'}, {row[1] for row in db.execute(queries['translationCandidates'], params)})
        self.assertEqual(0, db.execute(queries['canDeleteTranslationTypo'], dict(tagId='protected')).fetchone()[0])
        self.assertEqual(1, db.execute(queries['canDeleteTranslationTypo'], dict(tagId='review')).fetchone()[0])
        self.assertEqual(0, db.execute(queries['deleteTranslationTypo'], dict(tagId='protected')).rowcount)
        self.assertEqual(1, db.execute(queries['deleteTranslationTypo'], dict(tagId='review')).rowcount)


if __name__ == '__main__':
    unittest.main()
