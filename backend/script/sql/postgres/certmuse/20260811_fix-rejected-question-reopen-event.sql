\set ON_ERROR_STOP on
BEGIN;

-- The question service records a rejected-to-draft edit as "reopened".
-- Keep historical event names valid while allowing the active review workflow.
ALTER TABLE cm_question_revision_event
    DROP CONSTRAINT IF EXISTS ck_cm_question_revision_event_type;
ALTER TABLE cm_question_revision_event
    ADD CONSTRAINT ck_cm_question_revision_event_type CHECK (
        event_type IN (
            'created', 'migrated', 'submitted', 'approved', 'rejected', 'published', 'superseded', 'offline',
            'review_submitted', 'review_approved', 'review_rejected', 'question_offline', 'reopened'
        )
    );

ALTER TABLE cm_question_revision_event
    DROP CONSTRAINT IF EXISTS ck_cm_question_revision_event_transition;
ALTER TABLE cm_question_revision_event
    ADD CONSTRAINT ck_cm_question_revision_event_transition CHECK (
        (event_type IN ('created', 'migrated') AND from_status IS NULL AND to_status = 'draft')
        OR (event_type IN ('submitted', 'review_submitted') AND from_status IN ('draft', 'rejected') AND to_status = 'pending_review')
        OR (event_type IN ('approved') AND from_status = 'pending_review' AND to_status = 'approved')
        OR (event_type IN ('review_approved') AND from_status = 'pending_review' AND to_status = 'published')
        OR (event_type IN ('rejected', 'review_rejected') AND from_status = 'pending_review' AND to_status = 'rejected')
        OR (event_type = 'published' AND from_status = 'approved' AND to_status = 'published')
        OR (event_type = 'superseded' AND from_status = 'published' AND to_status = 'superseded')
        OR (event_type = 'offline' AND from_status = 'published' AND to_status = 'offline')
        OR (event_type = 'question_offline' AND from_status = 'published' AND to_status = 'draft')
        OR (event_type = 'reopened' AND from_status = 'rejected' AND to_status = 'draft')
    );

COMMIT;
