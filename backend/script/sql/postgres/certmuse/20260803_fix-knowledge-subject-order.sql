-- Align the existing knowledge tree with the question import subject numbering:
-- 1 = COMPREHENSIVE, 2 = CASE_ANALYSIS, 3 = ESSAY.
-- Earlier imports used subject_code alphabetical order, which attached the
-- 1.x/2.x/3.x knowledge-point branches to the wrong subjects.
BEGIN;

-- Questions already linked to this tree must move with their knowledge points.
-- Defer the two composite foreign keys until all three tables are aligned.
ALTER TABLE cm_question_knowledge
    ALTER CONSTRAINT fk_cm_question_knowledge_knowledge_point_id_exam_subject_id
    DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE cm_question_knowledge
    ALTER CONSTRAINT fk_cm_question_knowledge_question_id_exam_subject_id
    DEFERRABLE INITIALLY DEFERRED;

WITH knowledge_subject_mapping AS (
    SELECT
        kp.id AS knowledge_point_id,
        CASE
            WHEN kp.syllabus_number = '1' OR kp.syllabus_number LIKE '1.%' THEN sm.comprehensive_subject_id
            WHEN kp.syllabus_number = '2' OR kp.syllabus_number LIKE '2.%' THEN sm.case_analysis_subject_id
            WHEN kp.syllabus_number = '3' OR kp.syllabus_number LIKE '3.%' THEN sm.essay_subject_id
        END AS target_subject_id
    FROM cm_knowledge_point kp
    JOIN (
        SELECT
            sv.id AS syllabus_version_id,
            MAX(es.id) FILTER (WHERE es.subject_code = 'COMPREHENSIVE') AS comprehensive_subject_id,
            MAX(es.id) FILTER (WHERE es.subject_code = 'CASE_ANALYSIS') AS case_analysis_subject_id,
            MAX(es.id) FILTER (WHERE es.subject_code = 'ESSAY') AS essay_subject_id
        FROM cm_syllabus_version sv
        JOIN cm_exam_subject es ON es.certification_id = sv.certification_id
        WHERE sv.id = 900000000000000021
        GROUP BY sv.id
    ) sm ON sm.syllabus_version_id = kp.syllabus_version_id
    WHERE kp.syllabus_version_id = sm.syllabus_version_id
), question_subject_mapping AS (
    SELECT
        qk.question_id,
        MIN(ksm.target_subject_id) AS target_subject_id
    FROM cm_question_knowledge qk
    JOIN knowledge_subject_mapping ksm ON ksm.knowledge_point_id = qk.knowledge_point_id
    WHERE ksm.target_subject_id IS NOT NULL
    GROUP BY qk.question_id
    HAVING COUNT(DISTINCT ksm.target_subject_id) = 1
)
UPDATE cm_question q
SET exam_subject_id = qsm.target_subject_id
FROM question_subject_mapping qsm
WHERE q.id = qsm.question_id
  AND q.exam_subject_id <> qsm.target_subject_id;

UPDATE cm_question_knowledge qk
SET exam_subject_id = q.exam_subject_id
FROM cm_question q
WHERE q.id = qk.question_id
  AND qk.exam_subject_id <> q.exam_subject_id;

WITH subject_mapping AS (
    SELECT
        sv.id AS syllabus_version_id,
        MAX(es.id) FILTER (WHERE es.subject_code = 'COMPREHENSIVE') AS comprehensive_subject_id,
        MAX(es.id) FILTER (WHERE es.subject_code = 'CASE_ANALYSIS') AS case_analysis_subject_id,
        MAX(es.id) FILTER (WHERE es.subject_code = 'ESSAY') AS essay_subject_id
    FROM cm_syllabus_version sv
    JOIN cm_exam_subject es ON es.certification_id = sv.certification_id
    WHERE sv.id = 900000000000000021
    GROUP BY sv.id
)
UPDATE cm_knowledge_point kp
SET exam_subject_id = CASE
    WHEN kp.syllabus_number = '1' OR kp.syllabus_number LIKE '1.%' THEN sm.comprehensive_subject_id
    WHEN kp.syllabus_number = '2' OR kp.syllabus_number LIKE '2.%' THEN sm.case_analysis_subject_id
    WHEN kp.syllabus_number = '3' OR kp.syllabus_number LIKE '3.%' THEN sm.essay_subject_id
    ELSE kp.exam_subject_id
END
FROM subject_mapping sm
WHERE kp.syllabus_version_id = sm.syllabus_version_id
  AND sm.comprehensive_subject_id IS NOT NULL
  AND sm.case_analysis_subject_id IS NOT NULL
  AND sm.essay_subject_id IS NOT NULL
  AND (
      ((kp.syllabus_number = '1' OR kp.syllabus_number LIKE '1.%') AND kp.exam_subject_id <> sm.comprehensive_subject_id)
      OR ((kp.syllabus_number = '2' OR kp.syllabus_number LIKE '2.%') AND kp.exam_subject_id <> sm.case_analysis_subject_id)
      OR ((kp.syllabus_number = '3' OR kp.syllabus_number LIKE '3.%') AND kp.exam_subject_id <> sm.essay_subject_id)
  );

SET CONSTRAINTS
    fk_cm_question_knowledge_knowledge_point_id_exam_subject_id,
    fk_cm_question_knowledge_question_id_exam_subject_id
    IMMEDIATE;

ALTER TABLE cm_question_knowledge
    ALTER CONSTRAINT fk_cm_question_knowledge_knowledge_point_id_exam_subject_id
    NOT DEFERRABLE;
ALTER TABLE cm_question_knowledge
    ALTER CONSTRAINT fk_cm_question_knowledge_question_id_exam_subject_id
    NOT DEFERRABLE;

COMMIT;
