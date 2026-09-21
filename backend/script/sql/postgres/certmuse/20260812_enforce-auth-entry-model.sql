-- Phase B: run only after Phase A audit findings have been remediated and approved.
BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sys_role WHERE del_flag = '0' AND entry_type IS NULL) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: unclassified active roles remain';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_role
        WHERE del_flag = '0' AND entry_type NOT IN ('ADMIN', 'LEARNING')
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: invalid role entry type exists';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.role_id = ur.role_id
        WHERE r.status = '0' AND r.del_flag = '0'
        GROUP BY ur.user_id HAVING count(DISTINCT r.entry_type) > 1
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: mixed entry users exist';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_role r
        WHERE r.status = '0' AND r.del_flag = '0' AND r.entry_type = 'ADMIN'
          AND NOT EXISTS (
              SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
              WHERE rm.role_id = r.role_id AND m.perms = 'certmuse:entry:admin'
          )
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: an active ADMIN role lacks certmuse:entry:admin';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM sys_role r
        WHERE r.role_key = 'student' AND r.status = '0' AND r.del_flag = '0' AND r.entry_type = 'LEARNING'
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: active LEARNING student role is missing';
    END IF;
    IF EXISTS (
        SELECT 1 FROM sys_role r
        WHERE r.role_key = 'student' AND r.status = '0' AND r.del_flag = '0'
          AND NOT EXISTS (
              SELECT 1 FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id = rm.menu_id
              WHERE rm.role_id = r.role_id AND m.perms = 'certmuse:student'
          )
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: student role lacks certmuse:student';
    END IF;
    IF EXISTS (
        WITH learning_users AS (
            SELECT ur.user_id, bool_or(r.role_key = 'student') has_student_role
            FROM sys_user_role ur JOIN sys_role r ON r.role_id = ur.role_id
            WHERE r.status = '0' AND r.del_flag = '0' AND r.entry_type = 'LEARNING'
            GROUP BY ur.user_id
        )
        SELECT 1 FROM learning_users lu
        WHERE NOT lu.has_student_role
           OR NOT EXISTS (
               SELECT 1 FROM sys_user_role ur
               JOIN sys_role_menu rm ON rm.role_id = ur.role_id
               JOIN sys_menu m ON m.menu_id = rm.menu_id
               WHERE ur.user_id = lu.user_id AND m.perms = 'certmuse:student'
           )
    ) THEN
        RAISE EXCEPTION 'cannot enforce auth entry model: a LEARNING user lacks student role or entry permission';
    END IF;
END $$;

ALTER TABLE sys_role ALTER COLUMN entry_type SET NOT NULL;
ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS ck_sys_role_entry_type;
ALTER TABLE sys_role ADD CONSTRAINT ck_sys_role_entry_type CHECK (entry_type IN ('ADMIN', 'LEARNING'));

CREATE OR REPLACE FUNCTION certmuse_validate_single_role_entry(p_user_id bigint) RETURNS void AS $$
DECLARE entry_count integer;
BEGIN
    SELECT count(DISTINCT r.entry_type) INTO entry_count
    FROM sys_user_role ur JOIN sys_role r ON r.role_id = ur.role_id
    WHERE ur.user_id = p_user_id AND r.status = '0' AND r.del_flag = '0';
    IF entry_count > 1 THEN
        RAISE EXCEPTION 'user % cannot have ADMIN and LEARNING roles at the same time', p_user_id;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION certmuse_assert_single_role_entry() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM certmuse_validate_single_role_entry(OLD.user_id);
    ELSE
        PERFORM certmuse_validate_single_role_entry(NEW.user_id);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION certmuse_assert_role_entry_users() RETURNS trigger AS $$
DECLARE affected_user record;
BEGIN
    FOR affected_user IN SELECT user_id FROM sys_user_role WHERE role_id = NEW.role_id LOOP
        PERFORM certmuse_validate_single_role_entry(affected_user.user_id);
    END LOOP;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS ctr_sys_user_role_single_entry ON sys_user_role;
CREATE CONSTRAINT TRIGGER ctr_sys_user_role_single_entry
AFTER INSERT OR UPDATE OR DELETE ON sys_user_role DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION certmuse_assert_single_role_entry();

DROP TRIGGER IF EXISTS ctr_sys_role_single_entry ON sys_role;
CREATE CONSTRAINT TRIGGER ctr_sys_role_single_entry
AFTER UPDATE OF status, entry_type ON sys_role DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION certmuse_assert_role_entry_users();

COMMIT;
