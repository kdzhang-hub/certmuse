\set ON_ERROR_STOP on
BEGIN;

WITH timestamp_base AS (
    SELECT floor(extract(epoch FROM statement_timestamp()) * 1000)::bigint AS value
), default_subjects(subject_code, subject_name) AS (
    VALUES
        ('COMPREHENSIVE', '综合知识'),
        ('CASE_ANALYSIS', '案例分析'),
        ('ESSAY', '论文')
), missing_subjects AS (
    SELECT
        certification.id AS certification_id,
        subject.subject_code,
        subject.subject_name,
        row_number() OVER (ORDER BY certification.id, subject.subject_code) AS sequence_no
    FROM cm_exam_certification certification
    CROSS JOIN default_subjects subject
    WHERE certification.qualification_level = 'HIGH'
      AND NOT EXISTS (
          SELECT 1
          FROM cm_exam_subject existing_subject
          WHERE existing_subject.certification_id = certification.id
            AND existing_subject.subject_code = subject.subject_code
      )
)
INSERT INTO cm_exam_subject(id, certification_id, subject_code, subject_name, create_by, update_by)
SELECT
    (timestamp_base.value << 22) + missing_subjects.sequence_no,
    missing_subjects.certification_id,
    missing_subjects.subject_code,
    missing_subjects.subject_name,
    1,
    1
FROM missing_subjects
CROSS JOIN timestamp_base;

COMMIT;
