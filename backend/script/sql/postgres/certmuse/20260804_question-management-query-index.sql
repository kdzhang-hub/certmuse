BEGIN;

-- Support syllabus / knowledge-point EXISTS filters, including directory descendants.
CREATE INDEX IF NOT EXISTS idx_cm_question_knowledge_knowledge_revision
    ON cm_question_knowledge(knowledge_point_id, question_revision_id);

COMMIT;
