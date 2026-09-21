\set ON_ERROR_STOP on
BEGIN;

-- Imported knowledge points are immediately eligible for task recommendation.
-- Deleted and disabled rows remain excluded by the candidate query.
UPDATE cm_knowledge_point
   SET recommendation_enabled = true,
       update_time = now()
 WHERE del_flag = '0'
   AND status = '0'
   AND recommendation_enabled = false;

COMMIT;
