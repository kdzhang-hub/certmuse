\set ON_ERROR_STOP on
BEGIN;

-- U16 V1.2: the period is the publication aggregate.  Old schedules remain
-- readable only through their legacy parent, never through public queries.
CREATE TABLE cm_exam_period (
    id bigint PRIMARY KEY,
    period_code varchar(7) NOT NULL,
    exam_year integer NOT NULL,
    half varchar(2) NOT NULL,
    revision_no integer NOT NULL,
    supersedes_period_id bigint,
    record_origin varchar(10) NOT NULL DEFAULT 'official',
    status varchar(20) NOT NULL DEFAULT 'draft',
    official_source_url varchar(2048),
    source_published_at timestamptz,
    row_version bigint NOT NULL DEFAULT 0,
    published_by bigint,
    published_time timestamptz,
    create_by bigint,
    update_by bigint,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_cm_exam_period_code_revision UNIQUE(period_code, revision_no),
    CONSTRAINT fk_cm_exam_period_supersedes FOREIGN KEY(supersedes_period_id) REFERENCES cm_exam_period(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cm_exam_period_code CHECK (period_code ~ '^[0-9]{4}-H[12]$'
        AND exam_year = substring(period_code from 1 for 4)::integer
        AND half = substring(period_code from 6 for 2)),
    CONSTRAINT ck_cm_exam_period_half CHECK (half IN ('H1','H2')),
    CONSTRAINT ck_cm_exam_period_revision CHECK (revision_no > 0),
    CONSTRAINT ck_cm_exam_period_origin CHECK (record_origin IN ('official','legacy')),
    CONSTRAINT ck_cm_exam_period_status CHECK (status IN ('draft','published','superseded')),
    CONSTRAINT ck_cm_exam_period_version CHECK (row_version >= 0),
    CONSTRAINT ck_cm_exam_period_source CHECK (
        (record_origin = 'official' AND official_source_url IS NOT NULL AND source_published_at IS NOT NULL)
        OR (record_origin = 'legacy' AND status = 'superseded' AND official_source_url IS NULL AND source_published_at IS NULL)
    ),
    CONSTRAINT ck_cm_exam_period_publish_audit CHECK ((published_by IS NULL) = (published_time IS NULL))
);
CREATE UNIQUE INDEX uk_cm_exam_period_one_draft ON cm_exam_period(period_code) WHERE status='draft';
CREATE UNIQUE INDEX uk_cm_exam_period_one_published ON cm_exam_period(period_code) WHERE status='published';

CREATE TABLE cm_exam_region (
    id bigint PRIMARY KEY,
    region_code varchar(32) NOT NULL UNIQUE,
    region_name varchar(100) NOT NULL,
    region_type varchar(32) NOT NULL,
    parent_region_code varchar(32),
    institution_name varchar(300),
    contact_phone varchar(50),
    local_notice_url varchar(2048) NOT NULL,
    sort_order integer NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'enabled',
    row_version bigint NOT NULL DEFAULT 0,
    create_by bigint,
    update_by bigint,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_cm_exam_region_parent FOREIGN KEY(parent_region_code) REFERENCES cm_exam_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT ck_cm_exam_region_code CHECK (region_code ~ '^[A-Z0-9_]{2,32}$'),
    CONSTRAINT ck_cm_exam_region_type CHECK (region_type IN ('PROVINCE','AUTONOMOUS_REGION','MUNICIPALITY','CITY_SPECIAL','CORPS')),
    CONSTRAINT ck_cm_exam_region_parent CHECK (parent_region_code IS NULL OR parent_region_code <> region_code),
    CONSTRAINT ck_cm_exam_region_status CHECK (status IN ('enabled','disabled')),
    CONSTRAINT ck_cm_exam_region_sort CHECK (sort_order >= 0),
    CONSTRAINT ck_cm_exam_region_version CHECK (row_version >= 0),
    CONSTRAINT ck_cm_exam_region_notice_url CHECK (local_notice_url ~* '^https?://[^#[:space:]]+$')
);

CREATE TABLE cm_exam_region_registration (
    id bigint PRIMARY KEY,
    exam_period_id bigint NOT NULL,
    region_id bigint NOT NULL,
    registration_start timestamptz,
    registration_end timestamptz NOT NULL,
    review_start timestamptz,
    review_end timestamptz,
    payment_start timestamptz,
    payment_end timestamptz,
    qualification_review_note varchar(500),
    payment_note varchar(500),
    official_source_url varchar(2048) NOT NULL,
    source_published_at timestamptz NOT NULL,
    copied_from_id bigint,
    create_by bigint,
    update_by bigint,
    create_time timestamptz NOT NULL DEFAULT now(),
    update_time timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_cm_exam_region_registration_period_region UNIQUE(exam_period_id, region_id),
    CONSTRAINT fk_cm_exam_region_registration_period FOREIGN KEY(exam_period_id) REFERENCES cm_exam_period(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_exam_region_registration_region FOREIGN KEY(region_id) REFERENCES cm_exam_region(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cm_exam_region_registration_copied FOREIGN KEY(copied_from_id) REFERENCES cm_exam_region_registration(id) ON DELETE RESTRICT,
    CONSTRAINT ck_cm_exam_region_registration_window CHECK (registration_start IS NULL OR registration_start < registration_end),
    CONSTRAINT ck_cm_exam_region_registration_review CHECK ((review_start IS NULL) = (review_end IS NULL) AND (review_start IS NULL OR review_start < review_end)),
    CONSTRAINT ck_cm_exam_region_registration_payment CHECK ((payment_start IS NULL) = (payment_end IS NULL) AND (payment_start IS NULL OR payment_start < payment_end)),
    CONSTRAINT ck_cm_exam_region_registration_source CHECK (official_source_url ~* '^https://[^#[:space:]]+$')
);

ALTER TABLE cm_exam_schedule ADD COLUMN exam_period_id bigint;
ALTER TABLE cm_exam_schedule ADD COLUMN exam_start_date date;
ALTER TABLE cm_exam_schedule ADD COLUMN exam_end_date date;
ALTER TABLE cm_exam_schedule ADD COLUMN public_note varchar(500);
ALTER TABLE cm_exam_schedule ADD COLUMN copied_from_id bigint;
ALTER TABLE cm_exam_schedule_session ADD COLUMN certification_id bigint;

-- A legacy parent is a storage-only aggregate. Distinct old dates are kept
-- separate so no historical schedule is silently merged with another fact.
WITH legacy_dates AS (
    SELECT exam_date, extract(year FROM exam_date)::integer AS exam_year,
           CASE WHEN extract(month FROM exam_date) <= 6 THEN 'H1' ELSE 'H2' END AS half,
           row_number() OVER (PARTITION BY extract(year FROM exam_date), CASE WHEN extract(month FROM exam_date) <= 6 THEN 'H1' ELSE 'H2' END ORDER BY exam_date) AS revision_no
      FROM (SELECT DISTINCT exam_date FROM cm_exam_schedule) x
)
INSERT INTO cm_exam_period(id, period_code, exam_year, half, revision_no, record_origin, status, row_version)
SELECT (900000000000000000 + row_number() OVER (ORDER BY exam_date))::bigint,
       exam_year::text || '-' || half, exam_year, half, revision_no, 'legacy', 'superseded', 0
  FROM legacy_dates;

WITH legacy_dates AS (
    SELECT exam_date, extract(year FROM exam_date)::integer AS exam_year,
           CASE WHEN extract(month FROM exam_date) <= 6 THEN 'H1' ELSE 'H2' END AS half,
           row_number() OVER (PARTITION BY extract(year FROM exam_date), CASE WHEN extract(month FROM exam_date) <= 6 THEN 'H1' ELSE 'H2' END ORDER BY exam_date) AS revision_no
      FROM (SELECT DISTINCT exam_date FROM cm_exam_schedule) x
)
UPDATE cm_exam_schedule s
   SET exam_period_id = p.id, exam_start_date = s.exam_date, exam_end_date = s.exam_date,
       public_note = s.note
  FROM legacy_dates d
  JOIN cm_exam_period p ON p.period_code = d.exam_year::text || '-' || d.half
                       AND p.revision_no = d.revision_no AND p.record_origin = 'legacy'
 WHERE s.exam_date = d.exam_date;

UPDATE cm_exam_schedule_session ss SET certification_id = s.certification_id
  FROM cm_exam_schedule s WHERE s.id = ss.exam_schedule_id;

ALTER TABLE cm_exam_schedule ALTER COLUMN exam_period_id SET NOT NULL;
ALTER TABLE cm_exam_schedule ALTER COLUMN exam_start_date SET NOT NULL;
ALTER TABLE cm_exam_schedule ALTER COLUMN exam_end_date SET NOT NULL;
ALTER TABLE cm_exam_schedule_session ALTER COLUMN certification_id SET NOT NULL;
ALTER TABLE cm_exam_schedule ADD CONSTRAINT fk_cm_exam_schedule_period FOREIGN KEY(exam_period_id) REFERENCES cm_exam_period(id) ON DELETE RESTRICT;
ALTER TABLE cm_exam_schedule ADD CONSTRAINT uk_cm_exam_schedule_period_certification UNIQUE(exam_period_id, certification_id);
ALTER TABLE cm_exam_schedule ADD CONSTRAINT uk_cm_exam_schedule_id_certification UNIQUE(id, certification_id);
ALTER TABLE cm_exam_schedule ADD CONSTRAINT fk_cm_exam_schedule_copied FOREIGN KEY(copied_from_id) REFERENCES cm_exam_schedule(id) ON DELETE RESTRICT;
ALTER TABLE cm_exam_schedule ADD CONSTRAINT ck_cm_exam_schedule_dates CHECK (exam_start_date <= exam_end_date);
ALTER TABLE cm_exam_schedule_session ADD CONSTRAINT fk_cm_exam_schedule_session_schedule_certification FOREIGN KEY(exam_schedule_id, certification_id) REFERENCES cm_exam_schedule(id, certification_id) ON DELETE RESTRICT;
ALTER TABLE cm_exam_schedule_session ADD CONSTRAINT fk_cm_exam_schedule_session_subject_certification FOREIGN KEY(exam_subject_id, certification_id) REFERENCES cm_exam_subject(id, certification_id) ON DELETE RESTRICT;
ALTER TABLE cm_exam_schedule_session ADD CONSTRAINT uk_cm_exam_schedule_session_subject UNIQUE(exam_schedule_id, exam_subject_id);
ALTER TABLE cm_exam_schedule_session ADD CONSTRAINT ck_cm_exam_schedule_session_code CHECK (session_code ~ '^[A-Z0-9_]{2,32}$');
ALTER TABLE cm_exam_schedule_session DROP CONSTRAINT ck_cm_exam_schedule_session_sort_order;
ALTER TABLE cm_exam_schedule_session ADD CONSTRAINT ck_cm_exam_schedule_session_sort_order CHECK (sort_order BETWEEN 1 AND 99);

ALTER TABLE cm_exam_schedule DROP CONSTRAINT uk_cm_exam_schedule_certification_id_revision_no;
DROP INDEX IF EXISTS uk_cm_exam_schedule_draft;
DROP INDEX IF EXISTS uk_cm_exam_schedule_published;
DROP TRIGGER IF EXISTS trg_cm_exam_schedule_status ON cm_exam_schedule;

CREATE INDEX idx_cm_exam_schedule_period_dates ON cm_exam_schedule(exam_period_id, exam_start_date, exam_end_date);
CREATE INDEX idx_cm_exam_region_registration_period ON cm_exam_region_registration(exam_period_id, region_id);
CREATE INDEX idx_cm_exam_period_public ON cm_exam_period(period_code) WHERE status='published' AND record_origin='official';

-- Fixed directory supplied by the U16 contract. No runtime verification is performed.
INSERT INTO cm_exam_region(id,region_code,region_name,region_type,parent_region_code,institution_name,contact_phone,local_notice_url,sort_order) VALUES
(901000000000000010,'BEIJING','北京','MUNICIPALITY',NULL,'北京市人事考评办公室（北京市公务员考试测评中心）','010-12333','http://rsj.beijing.gov.cn/ywsite/bjpta/',10),
(901000000000000020,'TIANJIN','天津','MUNICIPALITY',NULL,'天津市工业和信息化研究院','022-23315758','http://www.tjiiti.org.cn/',20),
(901000000000000030,'HEBEI','河北','PROVINCE',NULL,'河北省人事考试中心','0311-83824748','http://www.hebpta.com.cn',30),
(901000000000000040,'SHANXI','山西','PROVINCE',NULL,'山西省数字化转型促进中心','0351-4040969','https://www.ruankao.org.cn/article/content/100003230309143819970466.html',40),
(901000000000000050,'INNER_MONGOLIA','内蒙古','AUTONOMOUS_REGION',NULL,'内蒙古自治区人事考试院','0471-6601168','http://www.impta.com.cn',50),
(901000000000000060,'LIAONING','辽宁','PROVINCE',NULL,'辽宁省工业和信息化发展研究院','024-88785206','http://www.lnicloud.com/',60),
(901000000000000070,'DALIAN','大连','CITY_SPECIAL','LIAONING','大连电子信息应用教育中心','0411-84609323','http://www.dlrkb.com',70),
(901000000000000080,'JILIN','吉林','PROVINCE',NULL,'吉林省人事考试中心','0431-12333-3','http://www.jlzkb.com/',80),
(901000000000000090,'HEILONGJIANG','黑龙江','PROVINCE',NULL,'黑龙江省人事考试中心','0451-82810152','http://www.hljrsks.org.cn',90),
(901000000000000100,'SHANGHAI','上海','MUNICIPALITY',NULL,'上海市职业能力考试院','021-12333','http://rsj.sh.gov.cn',100),
(901000000000000110,'JIANGSU','江苏','PROVINCE',NULL,'江苏省工业信息中心','025-69655785','https://www.jsiic.cn/',110),
(901000000000000120,'ZHEJIANG','浙江','PROVINCE',NULL,'浙江省科技宣传教育中心（浙江省软件考试实施中心）','0571-85118167','https://www.ruankao.org.cn/article/content/2502131006541603762472524.html',120),
(901000000000000130,'NINGBO','宁波','CITY_SPECIAL','ZHEJIANG','宁波市数字经济发展中心','0574-89183463','http://jxj.ningbo.gov.cn/',130),
(901000000000000140,'ANHUI','安徽','PROVINCE',NULL,'安徽省人事考试院','0551-63457905','http://www.apta.gov.cn',140),
(901000000000000150,'FUJIAN','福建','PROVINCE',NULL,'福建省经济和信息化技术中心','0591-87553103','http://gxt.fujian.gov.cn/zwgk/ztjj/fjrkzl/',150),
(901000000000000160,'JIANGXI','江西','PROVINCE',NULL,'江西省工业和信息化经济技术发展中心','0791-86266999','http://www.itetc.org',160),
(901000000000000170,'SHANDONG','山东','PROVINCE',NULL,'山东省人事考试中心','0531-88597886','http://hrss.shandong.gov.cn/rsks',170),
(901000000000000180,'HENAN','河南','PROVINCE',NULL,'河南省电子电气工程师协会','0371-65820502','http://www.chniee.org.cn/',180),
(901000000000000190,'HUBEI','湖北','PROVINCE',NULL,'湖北省中小企业服务中心（湖北省经济和信息化厅信息中心）','027-88874377','http://www.hbsme.com.cn/',190),
(901000000000000200,'HUNAN','湖南','PROVINCE',NULL,'湖南省工业和信息化行业事务中心','0731-83052648','http://gxt.hunan.gov.cn/rkb/',200),
(901000000000000210,'GUANGDONG','广东','PROVINCE',NULL,'广东省人事考试局','020-12333-9','http://rsks.gd.gov.cn',210),
(901000000000000220,'GUANGXI','广西','AUTONOMOUS_REGION',NULL,'广西壮族自治区人事考试院','12333 转区本级','http://www.gxpta.com.cn',220),
(901000000000000230,'HAINAN','海南','PROVINCE',NULL,'海南省人力资源开发局','0898-65375001','https://zhaopin.hainan.gov.cn',230),
(901000000000000240,'CHONGQING','重庆','MUNICIPALITY',NULL,'重庆市工业和信息化发展中心','023-88316967','https://cqitrk.cqitc.cn',240),
(901000000000000250,'SICHUAN','四川','PROVINCE',NULL,'四川省人事考试中心','028-86740101','http://rst.sc.gov.cn',250),
(901000000000000260,'GUIZHOU','贵州','PROVINCE',NULL,'贵州省信息中心','0851-88950123','http://www.gzsic.cn/',260),
(901000000000000270,'YUNNAN','云南','PROVINCE',NULL,'云南省信息技术发展中心','0871-63626248','http://www.ynxr.com',270),
(901000000000000280,'TIBET','西藏','AUTONOMOUS_REGION',NULL,'西藏自治区人事考试中心','0891-6845920','https://www.ruankao.org.cn/exam/contact.html',280),
(901000000000000290,'SHAANXI','陕西','PROVINCE',NULL,'陕西省科技资源统筹中心','029-81292882','http://www.shaanxirk.com',290),
(901000000000000300,'GANSU','甘肃','PROVINCE',NULL,'甘肃省人力资源考试中心','0931-4676230','http://ks.rst.gansu.gov.cn/ncms/index.shtml',300),
(901000000000000310,'QINGHAI','青海','PROVINCE',NULL,'青海省人事考试中心','0971-8258589','http://www.qhpta.com',310),
(901000000000000320,'NINGXIA','宁夏','AUTONOMOUS_REGION',NULL,'宁夏回族自治区人事考试中心','0951-5099130','http://www.nxpta.com',320),
(901000000000000330,'XINJIANG','新疆','AUTONOMOUS_REGION',NULL,'新疆维吾尔自治区工业经济和信息化研究院','0991-8874889','http://gxt.xinjiang.gov.cn/',330),
(901000000000000340,'XINJIANG_CORPS','新疆生产建设兵团','CORPS',NULL,'新疆生产建设兵团人力资源考试院','0991-8880763','http://btpta.xjbt.gov.cn/',340);

GRANT SELECT, INSERT, UPDATE, DELETE ON cm_exam_period, cm_exam_region, cm_exam_region_registration TO certmuse_app;

INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,query_param,is_frame,is_cache,menu_type,visible,status,perms,icon,active_menu,ext,create_by,create_time,update_by,update_time,remark) VALUES
(1761400000000099030,'官方考试安排查询',0,999,'',NULL,NULL,'N','N','F','1','0','certmuse:catalog:exam-schedule:list','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U16 permission'),
(1761400000000099031,'官方考试安排新增',0,999,'',NULL,NULL,'N','N','F','1','0','certmuse:catalog:exam-schedule:create','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U16 permission'),
(1761400000000099032,'官方考试安排编辑',0,999,'',NULL,NULL,'N','N','F','1','0','certmuse:catalog:exam-schedule:edit','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U16 permission'),
(1761400000000099033,'官方考试安排发布',0,999,'',NULL,NULL,'N','N','F','1','0','certmuse:catalog:exam-schedule:publish','#','','',1,CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP,'U16 permission')
ON CONFLICT (menu_id) DO UPDATE SET menu_name=EXCLUDED.menu_name,perms=EXCLUDED.perms,update_by=EXCLUDED.update_by,update_time=CURRENT_TIMESTAMP,remark=EXCLUDED.remark;
INSERT INTO sys_role_menu(role_id,menu_id) SELECT role_id,menu_id FROM sys_role CROSS JOIN (VALUES (1761400000000099030::bigint),(1761400000000099031::bigint),(1761400000000099032::bigint),(1761400000000099033::bigint)) AS permission(menu_id) WHERE role_key='superadmin' ON CONFLICT DO NOTHING;
COMMIT;
