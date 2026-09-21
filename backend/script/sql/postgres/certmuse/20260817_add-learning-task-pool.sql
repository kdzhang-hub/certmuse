-- U10 V1.2 learner task pool, frozen questions and single-session ownership.
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cm_learning_task) THEN
        RAISE EXCEPTION 'U10 V1.2 migration requires an empty cm_learning_task; historical tasks must be migrated explicitly';
    END IF;
END $$;

CREATE TABLE cm_task_replenishment_batch (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    goal_id bigint NOT NULL,
    trigger_type varchar(20) NOT NULL,
    triggered_time timestamptz DEFAULT now() NOT NULL,
    before_available_count integer NOT NULL,
    requested_count integer NOT NULL,
    created_count integer DEFAULT 0 NOT NULL,
    rule_version_id bigint,
    algorithm_version varchar(50) NOT NULL,
    input_snapshot jsonb NOT NULL,
    result_summary jsonb NOT NULL,
    status varchar(20) DEFAULT 'generating' NOT NULL,
    failure_reason varchar(100),
    request_id varchar(100),
    generation_key varchar(200) NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_task_replenishment_batch PRIMARY KEY (id),
    CONSTRAINT uk_cm_task_replenishment_batch_generation_key UNIQUE (generation_key),
    CONSTRAINT ck_cm_task_replenishment_batch_trigger_type CHECK (trigger_type IN ('scheduled','manual')),
    CONSTRAINT ck_cm_task_replenishment_batch_status CHECK (status IN ('generating','available','failed')),
    CONSTRAINT ck_cm_task_replenishment_batch_counts CHECK (
        before_available_count >= 0 AND requested_count >= 0 AND created_count >= 0
        AND created_count <= requested_count),
    CONSTRAINT ck_cm_task_replenishment_batch_input_schema CHECK (
        jsonb_typeof(input_snapshot)='object' AND input_snapshot ? 'schema_version'),
    CONSTRAINT ck_cm_task_replenishment_batch_result_schema CHECK (
        jsonb_typeof(result_summary)='object' AND result_summary ? 'schema_version'),
    CONSTRAINT fk_cm_task_replenishment_batch_goal FOREIGN KEY (goal_id)
        REFERENCES cm_user_goal(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_task_replenishment_batch_rule FOREIGN KEY (rule_version_id)
        REFERENCES cm_profile_rule_version(id) ON DELETE RESTRICT
);

CREATE TRIGGER trg_cm_task_replenishment_batch_status
    BEFORE UPDATE OF status ON cm_task_replenishment_batch
    FOR EACH ROW EXECUTE FUNCTION cm_guard_status_transition(
        'generating>available,generating>failed,failed>generating');

DROP TRIGGER IF EXISTS trg_cm_learning_task_status ON cm_learning_task;
DROP INDEX IF EXISTS idx_cm_learning_task_user_date;
ALTER TABLE cm_learning_task DROP CONSTRAINT IF EXISTS ck_cm_learning_task_status;
ALTER TABLE cm_learning_task DROP COLUMN task_date;
ALTER TABLE cm_learning_task DROP COLUMN status;
ALTER TABLE cm_learning_task DROP COLUMN status_changed_by;
ALTER TABLE cm_learning_task DROP COLUMN status_changed_time;
ALTER TABLE cm_learning_task DROP COLUMN status_reason;
ALTER TABLE cm_learning_task ADD COLUMN replenishment_batch_id bigint NOT NULL;
ALTER TABLE cm_learning_task ADD COLUMN display_snapshot jsonb NOT NULL;
ALTER TABLE cm_learning_task ADD CONSTRAINT fk_cm_learning_task_replenishment_batch
    FOREIGN KEY (replenishment_batch_id) REFERENCES cm_task_replenishment_batch(id) ON DELETE RESTRICT;
ALTER TABLE cm_learning_task ADD CONSTRAINT ck_cm_learning_task_task_type_u10
    CHECK (task_type='knowledge_bundle');
ALTER TABLE cm_learning_task ADD CONSTRAINT ck_cm_learning_task_display_snapshot_schema
    CHECK (jsonb_typeof(display_snapshot)='object' AND display_snapshot ? 'schema_version'
        AND display_snapshot ? 'title' AND display_snapshot ? 'knowledgePoint'
        AND display_snapshot ? 'recommendation');

ALTER TABLE cm_task_item DROP CONSTRAINT IF EXISTS ck_cm_task_item_status;
ALTER TABLE cm_task_item DROP COLUMN status;
ALTER TABLE cm_task_item ADD CONSTRAINT ck_cm_task_item_u10_order_type
    CHECK ((item_order=1 AND item_type='knowledge') OR (item_order=2 AND item_type='question'));

CREATE TABLE cm_task_question (
    id bigint NOT NULL,
    task_item_id bigint NOT NULL,
    question_id bigint NOT NULL,
    question_revision_id bigint NOT NULL,
    exam_subject_id bigint NOT NULL,
    question_order integer NOT NULL,
    evidence_group_key varchar(100) NOT NULL,
    difficulty_snapshot varchar(20) NOT NULL,
    estimated_seconds_snapshot integer NOT NULL,
    presentation_snapshot jsonb NOT NULL,
    grading_snapshot jsonb NOT NULL,
    knowledge_snapshot jsonb NOT NULL,
    scoring_point_snapshot jsonb NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_task_question PRIMARY KEY (id),
    CONSTRAINT uk_cm_task_question_order UNIQUE (task_item_id,question_order),
    CONSTRAINT uk_cm_task_question_revision UNIQUE (task_item_id,question_revision_id),
    CONSTRAINT ck_cm_task_question_order CHECK (question_order BETWEEN 1 AND 8),
    CONSTRAINT ck_cm_task_question_difficulty CHECK (difficulty_snapshot IN ('easy','medium','hard')),
    CONSTRAINT ck_cm_task_question_estimated_seconds CHECK (estimated_seconds_snapshot > 0),
    CONSTRAINT ck_cm_task_question_presentation_schema CHECK (
        jsonb_typeof(presentation_snapshot)='object' AND presentation_snapshot ? 'schema_version'),
    CONSTRAINT ck_cm_task_question_grading_schema CHECK (
        jsonb_typeof(grading_snapshot)='object' AND grading_snapshot ? 'schema_version'),
    CONSTRAINT ck_cm_task_question_knowledge_schema CHECK (
        jsonb_typeof(knowledge_snapshot)='object' AND knowledge_snapshot ? 'schema_version'),
    CONSTRAINT ck_cm_task_question_scoring_schema CHECK (
        jsonb_typeof(scoring_point_snapshot)='object' AND scoring_point_snapshot ? 'schema_version'),
    CONSTRAINT fk_cm_task_question_item FOREIGN KEY (task_item_id)
        REFERENCES cm_task_item(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_task_question_question FOREIGN KEY (question_id)
        REFERENCES cm_question(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_task_question_revision FOREIGN KEY (question_revision_id)
        REFERENCES cm_question_revision(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_task_question_subject FOREIGN KEY (exam_subject_id)
        REFERENCES cm_exam_subject(id) ON DELETE RESTRICT
);

ALTER TABLE cm_learning_session ADD COLUMN source_task_id bigint;
ALTER TABLE cm_learning_session ADD CONSTRAINT fk_cm_learning_session_source_task
    FOREIGN KEY (source_task_id) REFERENCES cm_learning_task(id) ON DELETE RESTRICT;
ALTER TABLE cm_learning_session ADD CONSTRAINT ck_cm_learning_session_daily_task_source
    CHECK (session_type<>'daily_task' OR source_task_id IS NOT NULL);
CREATE UNIQUE INDEX uk_cm_learning_session_daily_task_source
    ON cm_learning_session(source_task_id) WHERE session_type='daily_task';
CREATE UNIQUE INDEX uk_cm_learning_session_active_daily_task_goal
    ON cm_learning_session(user_id,goal_id)
    WHERE session_type='daily_task' AND status IN ('created','in_progress','submitted','settling');

CREATE INDEX idx_cm_learning_task_pool_list
    ON cm_learning_task(user_id,goal_id,create_time,id);
CREATE INDEX idx_cm_task_item_completion
    ON cm_task_item(task_id,item_order,id);
CREATE INDEX idx_cm_task_attempt_completed
    ON cm_task_attempt(task_item_id,attempt_no,status,completed_time);
CREATE INDEX idx_cm_task_replenishment_batch_goal
    ON cm_task_replenishment_batch(goal_id,triggered_time DESC,id DESC);
CREATE INDEX idx_cm_learning_session_source_task
    ON cm_learning_session(source_task_id,user_id,goal_id) WHERE session_type='daily_task';
CREATE INDEX idx_cm_task_question_item_order
    ON cm_task_question(task_item_id,question_order);

CREATE FUNCTION cm_validate_u10_task_bundle() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE target_task_id bigint; item_count integer; frozen_question_count integer;
        expected_question_count integer; first_question_order integer; last_question_order integer;
BEGIN
    IF TG_TABLE_NAME='cm_learning_task' THEN
        target_task_id := CASE WHEN TG_OP='DELETE' THEN OLD.id ELSE NEW.id END;
    ELSIF TG_TABLE_NAME='cm_task_item' THEN
        target_task_id := CASE WHEN TG_OP='DELETE' THEN OLD.task_id ELSE NEW.task_id END;
    ELSE
        SELECT task_id INTO target_task_id FROM cm_task_item
         WHERE id=CASE WHEN TG_OP='DELETE' THEN OLD.task_item_id ELSE NEW.task_item_id END;
    END IF;
    IF target_task_id IS NULL OR NOT EXISTS(SELECT 1 FROM cm_learning_task WHERE id=target_task_id) THEN
        RETURN NULL;
    END IF;
    SELECT count(*) INTO item_count FROM cm_task_item ti WHERE ti.task_id=target_task_id;
    IF item_count<>2
       OR NOT EXISTS(SELECT 1 FROM cm_task_item WHERE task_id=target_task_id AND item_order=1 AND item_type='knowledge')
       OR NOT EXISTS(SELECT 1 FROM cm_task_item WHERE task_id=target_task_id AND item_order=2 AND item_type='question') THEN
        RAISE EXCEPTION 'U10 task % must contain exactly the knowledge and question items',target_task_id;
    END IF;
    SELECT count(*),min(tq.question_order),max(tq.question_order)
      INTO frozen_question_count,first_question_order,last_question_order FROM cm_task_question tq
      JOIN cm_task_item ti ON ti.id=tq.task_item_id
     WHERE ti.task_id=target_task_id AND ti.item_order=2 AND ti.item_type='question';
    SELECT (ti.target_data->>'questionCount')::integer INTO expected_question_count
      FROM cm_task_item ti
     WHERE ti.task_id=target_task_id AND ti.item_order=2 AND ti.item_type='question';
    IF expected_question_count IS NULL
       OR frozen_question_count NOT BETWEEN 1 AND 8
       OR first_question_order<>1 OR last_question_order<>frozen_question_count
       OR expected_question_count<>frozen_question_count THEN
        RAISE EXCEPTION 'U10 task % must contain one to eight contiguous frozen questions matching questionCount',target_task_id;
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER ctr_cm_learning_task_u10_bundle
    AFTER INSERT OR UPDATE ON cm_learning_task DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cm_validate_u10_task_bundle();
CREATE CONSTRAINT TRIGGER ctr_cm_task_item_u10_bundle
    AFTER INSERT OR UPDATE OR DELETE ON cm_task_item DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cm_validate_u10_task_bundle();
CREATE CONSTRAINT TRIGGER ctr_cm_task_question_u10_bundle
    AFTER INSERT OR UPDATE OR DELETE ON cm_task_question DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cm_validate_u10_task_bundle();

DO $$
DECLARE permission_id bigint; permission_code text;
BEGIN
    FOR permission_id,permission_code IN
        SELECT * FROM (VALUES
            (1761400000000099011::bigint,'certmuse:learning:task:query'::text),
            (1761400000000099012::bigint,'certmuse:learning:task:launch'::text),
            (1761400000000099013::bigint,'certmuse:learning:task:supplement'::text)
        ) p(id,code)
    LOOP
        IF EXISTS(SELECT 1 FROM sys_menu WHERE menu_id=permission_id AND perms<>permission_code)
           OR EXISTS(SELECT 1 FROM sys_menu WHERE perms=permission_code AND menu_id<>permission_id) THEN
            RAISE EXCEPTION 'U10 permission conflict: % / %',permission_id,permission_code;
        END IF;
    END LOOP;
END $$;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
    menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
VALUES
    (1761400000000099011,'学员学习任务查询',0,999,'',NULL,NULL,'N','N','F','1','0',
     'certmuse:learning:task:query','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U10 hidden learner permission'),
    (1761400000000099012,'学员学习任务启动',0,999,'',NULL,NULL,'N','N','F','1','0',
     'certmuse:learning:task:launch','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U10 hidden learner permission'),
    (1761400000000099013,'学员学习任务补充',0,999,'',NULL,NULL,'N','N','F','1','0',
     'certmuse:learning:task:supplement','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U10 hidden learner permission')
ON CONFLICT(menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
    update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT role_id,permission_id FROM sys_role
CROSS JOIN (VALUES (1761400000000099011::bigint),(1761400000000099012::bigint),
    (1761400000000099013::bigint)) p(permission_id)
WHERE role_key='student' AND del_flag='0' ON CONFLICT DO NOTHING;

COMMIT;
