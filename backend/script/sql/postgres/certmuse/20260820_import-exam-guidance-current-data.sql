\set ON_ERROR_STOP on

-- Import the verified 2026 exam-guidance data as a formal, transactional
-- production migration. This is the immutable release form of
-- tools/ops/import-exam-guidance-current-2026.sql (SHA-256
-- c45ba0ec6849402201c113064399fd69a13a5ee48102c5019a880fd6fd34eaae).
-- It does not publish a period and does not create regional registration data.
--
-- A pre-release production seed accidentally assigned NULL_1 to the existing
-- System Architect certification. Preserve its primary key and subjects, and
-- correct only the uniquely identified record before importing the schedule.

BEGIN;

DO $$
DECLARE
    candidate_ids bigint[];
    candidate_count integer;
BEGIN
    IF to_regclass('public.cm_exam_period') IS NULL
       OR to_regclass('public.cm_exam_region') IS NULL
       OR to_regclass('public.cm_exam_schedule') IS NULL
       OR to_regclass('public.cm_exam_schedule_session') IS NULL THEN
        RAISE EXCEPTION 'U16 exam-guidance tables are missing; apply migrations first';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM cm_exam_period
        WHERE period_code IN ('2026-H1', '2026-H2')
          AND revision_no = 1
          AND (record_origin <> 'official' OR status <> 'draft')
    ) THEN
        RAISE EXCEPTION 'refusing to overwrite a non-draft or non-official 2026 period';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM cm_exam_certification
        WHERE certification_code = 'SYSTEM_ARCHITECT'
    ) THEN
        SELECT array_agg(candidate.id ORDER BY candidate.id)
        INTO candidate_ids
        FROM (
            SELECT certification.id
            FROM cm_exam_certification certification
            JOIN cm_exam_subject subject ON subject.certification_id = certification.id
            WHERE certification.certification_code = 'NULL_1'
              AND certification.qualification_level = 'HIGH'
              AND certification.status = '0'
            GROUP BY certification.id
            HAVING count(*) = 3
               AND count(*) FILTER (
                   WHERE subject.subject_code IN ('COMPREHENSIVE', 'CASE_ANALYSIS', 'ESSAY')
               ) = 3
        ) candidate;

        candidate_count := COALESCE(array_length(candidate_ids, 1), 0);
        IF candidate_count <> 1 THEN
            RAISE EXCEPTION
                'expected exactly one HIGH/enabled NULL_1 certification with the three System Architect subjects, found %',
                candidate_count;
        END IF;

        UPDATE cm_exam_certification
        SET certification_code = 'SYSTEM_ARCHITECT',
            update_time = CURRENT_TIMESTAMP
        WHERE id = candidate_ids[1]
          AND certification_code = 'NULL_1'
          AND qualification_level = 'HIGH'
          AND status = '0';

        IF NOT FOUND THEN
            RAISE EXCEPTION 'System Architect certification code correction did not update the verified candidate';
        END IF;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM cm_exam_subject subject
        JOIN cm_exam_certification certification ON certification.id = subject.certification_id
        WHERE certification.certification_code = 'SYSTEM_ARCHITECT'
          AND subject.subject_code IN ('COMPREHENSIVE', 'CASE_ANALYSIS', 'ESSAY')
        GROUP BY certification.id
        HAVING count(*) = 3
    ) THEN
        RAISE EXCEPTION 'SYSTEM_ARCHITECT and its three subjects must exist before importing the 2026-H1 schedule';
    END IF;
END $$;

-- The interface only displays the source publication date. Timestamps are
-- stored at the beginning of that source day in Asia/Shanghai.
INSERT INTO cm_exam_period (
    id, period_code, exam_year, half, revision_no, record_origin, status,
    official_source_url, source_published_at, row_version
)
VALUES
    (902000000000000010, '2026-H1', 2026, 'H1', 1, 'official', 'draft',
     'https://www.ruankao.org.cn/article/content/2604141656059313900690012.html',
     '2026-04-14T00:00:00+08:00', 0),
    (902000000000000020, '2026-H2', 2026, 'H2', 1, 'official', 'draft',
     'https://www.ruankao.org.cn/article/content/2603051204118156470700001.html',
     '2026-03-05T00:00:00+08:00', 0)
ON CONFLICT (period_code, revision_no) DO UPDATE SET
    exam_year = EXCLUDED.exam_year,
    half = EXCLUDED.half,
    official_source_url = EXCLUDED.official_source_url,
    source_published_at = EXCLUDED.source_published_at,
    row_version = cm_exam_period.row_version + 1,
    update_time = CURRENT_TIMESTAMP
WHERE cm_exam_period.record_origin = 'official'
  AND cm_exam_period.status = 'draft'
  AND ROW(
      cm_exam_period.exam_year,
      cm_exam_period.half,
      cm_exam_period.official_source_url,
      cm_exam_period.source_published_at
  ) IS DISTINCT FROM ROW(
      EXCLUDED.exam_year,
      EXCLUDED.half,
      EXCLUDED.official_source_url,
      EXCLUDED.source_published_at
  );

-- Current verified historical schedule only. The two morning subjects share
-- one official continuous time range; no unannounced intermediate boundary is invented.
INSERT INTO cm_exam_schedule (
    id, certification_id, revision_no, exam_date, registration_start,
    registration_end, timezone, note, status, row_version, exam_period_id,
    exam_start_date, exam_end_date, public_note
)
SELECT
    902000000000000110,
    certification.id,
    1,
    DATE '2026-05-23',
    NULL,
    NULL,
    'Asia/Shanghai',
    NULL,
    'draft',
    0,
    period.id,
    DATE '2026-05-23',
    DATE '2026-05-23',
    '第一批：上午综合知识、案例分析连考；下午论文。'
FROM cm_exam_period period
JOIN cm_exam_certification certification ON certification.certification_code = 'SYSTEM_ARCHITECT'
WHERE period.period_code = '2026-H1' AND period.revision_no = 1
ON CONFLICT (exam_period_id, certification_id) DO UPDATE SET
    revision_no = EXCLUDED.revision_no,
    exam_date = EXCLUDED.exam_date,
    registration_start = NULL,
    registration_end = NULL,
    timezone = EXCLUDED.timezone,
    note = NULL,
    exam_start_date = EXCLUDED.exam_start_date,
    exam_end_date = EXCLUDED.exam_end_date,
    public_note = EXCLUDED.public_note,
    row_version = cm_exam_schedule.row_version + 1,
    update_time = CURRENT_TIMESTAMP
WHERE cm_exam_schedule.status = 'draft'
  AND ROW(
      cm_exam_schedule.revision_no,
      cm_exam_schedule.exam_date,
      cm_exam_schedule.registration_start,
      cm_exam_schedule.registration_end,
      cm_exam_schedule.timezone,
      cm_exam_schedule.note,
      cm_exam_schedule.exam_start_date,
      cm_exam_schedule.exam_end_date,
      cm_exam_schedule.public_note
  ) IS DISTINCT FROM ROW(
      EXCLUDED.revision_no,
      EXCLUDED.exam_date,
      EXCLUDED.registration_start,
      EXCLUDED.registration_end,
      EXCLUDED.timezone,
      EXCLUDED.note,
      EXCLUDED.exam_start_date,
      EXCLUDED.exam_end_date,
      EXCLUDED.public_note
  );

INSERT INTO cm_exam_schedule_session (
    id, exam_schedule_id, exam_subject_id, session_code, start_time, end_time,
    sort_order, certification_id
)
SELECT
    item.id,
    schedule.id,
    subject.id,
    item.session_code,
    item.start_time,
    item.end_time,
    item.sort_order,
    certification.id
FROM (
    VALUES
        (902000000000000111::bigint, 'COMPREHENSIVE', 'B1_AM_KNOWLEDGE', '2026-05-23T08:30:00+08:00'::timestamptz, '2026-05-23T12:30:00+08:00'::timestamptz, 1),
        (902000000000000112::bigint, 'CASE_ANALYSIS', 'B1_AM_CASE', '2026-05-23T08:30:00+08:00'::timestamptz, '2026-05-23T12:30:00+08:00'::timestamptz, 2),
        (902000000000000113::bigint, 'ESSAY', 'B1_PM_ESSAY', '2026-05-23T14:30:00+08:00'::timestamptz, '2026-05-23T16:30:00+08:00'::timestamptz, 3)
) AS item(id, subject_code, session_code, start_time, end_time, sort_order)
JOIN cm_exam_certification certification ON certification.certification_code = 'SYSTEM_ARCHITECT'
JOIN cm_exam_subject subject ON subject.certification_id = certification.id AND subject.subject_code = item.subject_code
JOIN cm_exam_period period ON period.period_code = '2026-H1' AND period.revision_no = 1
JOIN cm_exam_schedule schedule ON schedule.exam_period_id = period.id AND schedule.certification_id = certification.id
ON CONFLICT (exam_schedule_id, exam_subject_id) DO UPDATE SET
    session_code = EXCLUDED.session_code,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    sort_order = EXCLUDED.sort_order,
    certification_id = EXCLUDED.certification_id
WHERE ROW(
    cm_exam_schedule_session.session_code,
    cm_exam_schedule_session.start_time,
    cm_exam_schedule_session.end_time,
    cm_exam_schedule_session.sort_order,
    cm_exam_schedule_session.certification_id
) IS DISTINCT FROM ROW(
    EXCLUDED.session_code,
    EXCLUDED.start_time,
    EXCLUDED.end_time,
    EXCLUDED.sort_order,
    EXCLUDED.certification_id
);

-- Long-term regional directory. These are the values presently in the local
-- database. Several URLs are HTTP or a national contact page; retain them for
-- parity only, then replace each with a verified local official entry before
-- publishing regional registration information.
INSERT INTO cm_exam_region (
    id, region_code, region_name, region_type, parent_region_code,
    institution_name, contact_phone, local_notice_url, sort_order, status, row_version
)
VALUES
    (901000000000000010, 'BEIJING', '北京', 'MUNICIPALITY', NULL, '北京市人事考评办公室（北京市公务员考试测评中心）', '010-12333', 'http://rsj.beijing.gov.cn/ywsite/bjpta/', 10, 'enabled', 0),
    (901000000000000020, 'TIANJIN', '天津', 'MUNICIPALITY', NULL, '天津市工业和信息化研究院', '022-23315758', 'https://www.ruankao.org.cn/exam/contact.html', 20, 'enabled', 0),
    (901000000000000030, 'HEBEI', '河北', 'PROVINCE', NULL, '河北省人事考试中心', '0311-83824748', 'http://www.hebpta.com.cn', 30, 'enabled', 0),
    (901000000000000040, 'SHANXI', '山西', 'PROVINCE', NULL, '山西省数字化转型促进中心', '0351-4040969', 'https://www.ruankao.org.cn/article/content/100003230309143819970466.html', 40, 'enabled', 0),
    (901000000000000050, 'INNER_MONGOLIA', '内蒙古', 'AUTONOMOUS_REGION', NULL, '内蒙古自治区人事考试院', '0471-6601168', 'http://www.impta.com.cn', 50, 'enabled', 0),
    (901000000000000060, 'LIAONING', '辽宁', 'PROVINCE', NULL, '辽宁省工业和信息化发展研究院', '024-88785206', 'http://www.lnicloud.com/', 60, 'enabled', 0),
    (901000000000000070, 'DALIAN', '大连', 'CITY_SPECIAL', 'LIAONING', '大连电子信息应用教育中心', '0411-84609323', 'http://www.dlrkb.com', 70, 'enabled', 0),
    (901000000000000080, 'JILIN', '吉林', 'PROVINCE', NULL, '吉林省人事考试中心', '0431-12333-3', 'http://www.jlzkb.com/', 80, 'enabled', 0),
    (901000000000000090, 'HEILONGJIANG', '黑龙江', 'PROVINCE', NULL, '黑龙江省人事考试中心', '0451-82810152', 'http://www.hljrsks.org.cn', 90, 'enabled', 0),
    (901000000000000100, 'SHANGHAI', '上海', 'MUNICIPALITY', NULL, '上海市职业能力考试院', '021-12333', 'http://rsj.sh.gov.cn', 100, 'enabled', 0),
    (901000000000000110, 'JIANGSU', '江苏', 'PROVINCE', NULL, '江苏省工业信息中心', '025-69655785', 'https://www.jsiic.cn/', 110, 'enabled', 0),
    (901000000000000120, 'ZHEJIANG', '浙江', 'PROVINCE', NULL, '浙江省科技宣传教育中心（浙江省软件考试实施中心）', '0571-85118167', 'https://www.ruankao.org.cn/article/content/2502131006541603762472524.html', 120, 'enabled', 0),
    (901000000000000130, 'NINGBO', '宁波', 'CITY_SPECIAL', 'ZHEJIANG', '宁波市数字经济发展中心', '0574-89183463', 'https://www.ruankao.org.cn/exam/contact.html', 130, 'enabled', 0),
    (901000000000000140, 'ANHUI', '安徽', 'PROVINCE', NULL, '安徽省人事考试院', '0551-63457905', 'http://www.apta.gov.cn', 140, 'enabled', 0),
    (901000000000000150, 'FUJIAN', '福建', 'PROVINCE', NULL, '福建省经济和信息化技术中心', '0591-87553103', 'http://gxt.fujian.gov.cn/zwgk/ztjj/fjrkzl/', 150, 'enabled', 0),
    (901000000000000160, 'JIANGXI', '江西', 'PROVINCE', NULL, '江西省工业和信息化经济技术发展中心', '0791-86266999', 'http://www.itetc.org', 160, 'enabled', 0),
    (901000000000000170, 'SHANDONG', '山东', 'PROVINCE', NULL, '山东省人事考试中心', '0531-88597886', 'http://hrss.shandong.gov.cn/rsks', 170, 'enabled', 0),
    (901000000000000180, 'HENAN', '河南', 'PROVINCE', NULL, '河南省电子电气工程师协会', '0371-65820502', 'http://www.chniee.org.cn/', 180, 'enabled', 0),
    (901000000000000190, 'HUBEI', '湖北', 'PROVINCE', NULL, '湖北省中小企业服务中心（湖北省经济和信息化厅信息中心）', '027-88874377', 'http://www.hbsme.com.cn/', 190, 'enabled', 0),
    (901000000000000200, 'HUNAN', '湖南', 'PROVINCE', NULL, '湖南省工业和信息化行业事务中心', '0731-83052648', 'http://gxt.hunan.gov.cn/rkb/', 200, 'enabled', 0),
    (901000000000000210, 'GUANGDONG', '广东', 'PROVINCE', NULL, '广东省人事考试局', '020-12333-9', 'https://www.ruankao.org.cn/exam/contact.html', 210, 'enabled', 0),
    (901000000000000220, 'GUANGXI', '广西', 'AUTONOMOUS_REGION', NULL, '广西壮族自治区人事考试院', '12333 转区本级', 'http://www.gxpta.com.cn', 220, 'enabled', 0),
    (901000000000000230, 'HAINAN', '海南', 'PROVINCE', NULL, '海南省人力资源开发局', '0898-65375001', 'https://zhaopin.hainan.gov.cn', 230, 'enabled', 0),
    (901000000000000240, 'CHONGQING', '重庆', 'MUNICIPALITY', NULL, '重庆市工业和信息化发展中心', '023-88316967', 'https://cqitrk.cqitc.cn', 240, 'enabled', 0),
    (901000000000000250, 'SICHUAN', '四川', 'PROVINCE', NULL, '四川省人事考试中心', '028-86740101', 'http://rst.sc.gov.cn', 250, 'enabled', 0),
    (901000000000000260, 'GUIZHOU', '贵州', 'PROVINCE', NULL, '贵州省信息中心', '0851-88950123', 'http://www.gzsic.cn/', 260, 'enabled', 0),
    (901000000000000270, 'YUNNAN', '云南', 'PROVINCE', NULL, '云南省信息技术发展中心', '0871-63626248', 'https://www.ynxr.com/', 270, 'enabled', 0),
    (901000000000000280, 'TIBET', '西藏', 'AUTONOMOUS_REGION', NULL, '西藏自治区人事考试中心', '0891-6845920', 'https://www.ruankao.org.cn/exam/contact.html', 280, 'enabled', 0),
    (901000000000000290, 'SHAANXI', '陕西', 'PROVINCE', NULL, '陕西省科技资源统筹中心', '029-81292882', 'http://www.shaanxirk.com', 290, 'enabled', 0),
    (901000000000000300, 'GANSU', '甘肃', 'PROVINCE', NULL, '甘肃省人力资源考试中心', '0931-4676230', 'http://ks.rst.gansu.gov.cn/ncms/index.shtml', 300, 'enabled', 0),
    (901000000000000310, 'QINGHAI', '青海', 'PROVINCE', NULL, '青海省人事考试中心', '0971-8258589', 'http://www.qhpta.com', 310, 'enabled', 0),
    (901000000000000320, 'NINGXIA', '宁夏', 'AUTONOMOUS_REGION', NULL, '宁夏回族自治区人事考试中心', '0951-5099130', 'http://www.nxpta.com', 320, 'enabled', 0),
    (901000000000000330, 'XINJIANG', '新疆', 'AUTONOMOUS_REGION', NULL, '新疆维吾尔自治区工业经济和信息化研究院', '0991-8874889', 'http://gxt.xinjiang.gov.cn/', 330, 'enabled', 0),
    (901000000000000340, 'XINJIANG_CORPS', '新疆生产建设兵团', 'CORPS', NULL, '新疆生产建设兵团人力资源考试院', '0991-8880763', 'http://btpta.xjbt.gov.cn/', 340, 'enabled', 0)
ON CONFLICT (region_code) DO UPDATE SET
    region_name = EXCLUDED.region_name,
    region_type = EXCLUDED.region_type,
    parent_region_code = EXCLUDED.parent_region_code,
    institution_name = EXCLUDED.institution_name,
    contact_phone = EXCLUDED.contact_phone,
    local_notice_url = EXCLUDED.local_notice_url,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    row_version = cm_exam_region.row_version + 1,
    update_time = CURRENT_TIMESTAMP
WHERE ROW(
    cm_exam_region.region_name,
    cm_exam_region.region_type,
    cm_exam_region.parent_region_code,
    cm_exam_region.institution_name,
    cm_exam_region.contact_phone,
    cm_exam_region.local_notice_url,
    cm_exam_region.sort_order,
    cm_exam_region.status
) IS DISTINCT FROM ROW(
    EXCLUDED.region_name,
    EXCLUDED.region_type,
    EXCLUDED.parent_region_code,
    EXCLUDED.institution_name,
    EXCLUDED.contact_phone,
    EXCLUDED.local_notice_url,
    EXCLUDED.sort_order,
    EXCLUDED.status
);

COMMIT;
