-- Learner question AI chat persistence and permission.
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE cm_ai_conversation (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    practice_session_id bigint NOT NULL,
    question_order integer NOT NULL,
    status varchar(20) DEFAULT 'ACTIVE' NOT NULL,
    provider varchar(50) NOT NULL,
    model_name varchar(100) NOT NULL,
    create_time timestamptz DEFAULT now() NOT NULL,
    update_time timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT pk_cm_ai_conversation PRIMARY KEY (id),
    CONSTRAINT fk_cm_ai_conversation_session FOREIGN KEY (practice_session_id)
        REFERENCES cm_learning_session(id) ON DELETE CASCADE,
    CONSTRAINT uk_cm_ai_conversation_item UNIQUE (user_id, practice_session_id, question_order),
    CONSTRAINT ck_cm_ai_conversation_order CHECK (question_order > 0),
    CONSTRAINT ck_cm_ai_conversation_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_cm_ai_conversation_owner ON cm_ai_conversation(user_id, id);

CREATE TABLE cm_ai_message (
    id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    reply_to_message_id bigint,
    sequence_no integer NOT NULL,
    role varchar(20) NOT NULL,
    content text DEFAULT '' NOT NULL,
    status varchar(20) NOT NULL,
    answer_disclosure_mode varchar(30),
    error_code varchar(100),
    client_message_id varchar(36),
    create_time timestamptz DEFAULT now() NOT NULL,
    completed_time timestamptz,
    CONSTRAINT pk_cm_ai_message PRIMARY KEY (id),
    CONSTRAINT fk_cm_ai_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES cm_ai_conversation(id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_ai_message_reply FOREIGN KEY (reply_to_message_id)
        REFERENCES cm_ai_message(id) ON DELETE CASCADE,
    CONSTRAINT uk_cm_ai_message_sequence UNIQUE (conversation_id, sequence_no),
    CONSTRAINT uk_cm_ai_message_reply UNIQUE (reply_to_message_id),
    CONSTRAINT ck_cm_ai_message_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_cm_ai_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_cm_ai_message_status CHECK (status IN ('COMPLETED', 'GENERATING', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_cm_ai_message_disclosure CHECK
        ((role='USER' AND answer_disclosure_mode IS NULL) OR
         (role='ASSISTANT' AND answer_disclosure_mode IN ('GUIDANCE_ONLY', 'FULL_EXPLANATION')))
);

CREATE UNIQUE INDEX uk_cm_ai_message_client_id
    ON cm_ai_message(client_message_id) WHERE role='USER' AND client_message_id IS NOT NULL;
CREATE INDEX idx_cm_ai_message_history ON cm_ai_message(conversation_id, sequence_no DESC);
CREATE INDEX idx_cm_ai_message_stale ON cm_ai_message(create_time)
    WHERE status='GENERATING';

CREATE UNIQUE INDEX uk_cm_idempotency_practice_ai_request
    ON cm_idempotency_record(request_id)
    WHERE action_code IN ('CREATE_PRACTICE_AI_CONVERSATION', 'CANCEL_PRACTICE_AI_GENERATION');

DO $$
DECLARE permission_id bigint := 1761400000000099018;
BEGIN
    IF EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=permission_id
               AND perms<>'certmuse:assessment:knowledge-practice:ai-chat')
       OR EXISTS (SELECT 1 FROM sys_menu WHERE perms='certmuse:assessment:knowledge-practice:ai-chat'
                  AND menu_id<>permission_id) THEN
        RAISE EXCEPTION 'practice AI chat permission id conflict';
    END IF;
    INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,
        menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark)
    VALUES (permission_id,'学员题目AI助教',0,999,'',NULL,NULL,'N','N','F','1','0',
        'certmuse:assessment:knowledge-practice:ai-chat','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,
        'CertMuse hidden learner permission')
    ON CONFLICT (menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,status='0',
        update_by=1,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT role_id,permission_id FROM sys_role WHERE role_key='student' AND del_flag='0'
    ON CONFLICT DO NOTHING;
END $$;

COMMIT;
