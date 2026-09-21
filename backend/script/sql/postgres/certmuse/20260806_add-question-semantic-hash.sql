BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE cm_question_revision ADD COLUMN IF NOT EXISTS semantic_hash varchar(64);

-- Existing published revisions are immutable. This one-time derived-field backfill
-- must not change their content, so temporarily bypass only that table trigger
-- within this transaction; rollback restores the trigger on any failure.
ALTER TABLE cm_question_revision DISABLE TRIGGER trg_cm_question_revision_immutable;

WITH canonical_fingerprints AS (
    SELECT r.id,
        r.question_type || E'\n' || btrim(regexp_replace(coalesce(r.stem, ''), '[[:space:]]+', ' ', 'g')) || E'\n' ||
        CASE WHEN r.question_type = 'CHOICE' THEN coalesce(
            string_agg(
                btrim(regexp_replace(coalesce(o.option_text, ''), '[[:space:]]+', ' ', 'g')),
                E'\n' ORDER BY btrim(regexp_replace(coalesce(o.option_text, ''), '[[:space:]]+', ' ', 'g'))
            ),
            ''
        ) ELSE '' END AS canonical_value
    FROM cm_question_revision r
    LEFT JOIN cm_question_option o ON o.question_revision_id = r.id
    GROUP BY r.id, r.question_type, r.stem
)
UPDATE cm_question_revision r
SET semantic_hash = encode(digest(canonical_fingerprints.canonical_value, 'sha256'), 'hex')
FROM canonical_fingerprints
WHERE r.id = canonical_fingerprints.id
  AND r.semantic_hash IS NULL;

ALTER TABLE cm_question_revision ENABLE TRIGGER trg_cm_question_revision_immutable;

ALTER TABLE cm_question_revision ALTER COLUMN semantic_hash SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_cm_question_revision_semantic_hash ON cm_question_revision(semantic_hash);

COMMIT;
