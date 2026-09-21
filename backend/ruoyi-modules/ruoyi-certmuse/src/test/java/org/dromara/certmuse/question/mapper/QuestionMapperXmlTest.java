package org.dromara.certmuse.question.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.question.domain.bo.QuestionQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class QuestionMapperXmlTest {
    @Test
    void parsesStatementsAndKeepsListProjectionSafe() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.QuestionMapper.";
        assertThat(configuration.hasStatement(namespace + "selectQuestions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockRevision")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockReviewRevision")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockReviewRevisions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "updateRevision")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markReviewSubmitted")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markReviewPublished")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markReviewRejected")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markPublishedDraft")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertReviewIdempotency")).isTrue();
        assertThat(configuration.hasStatement(namespace + "softDeleteQuestion")).isTrue();

        QuestionQueryBo query = new QuestionQueryBo();
        query.setKeyword("架构");
        query.setIncludeDescendants(true);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("certificationId", 1L);
        parameters.put("syllabusId", 21L);
        parameters.put("subjectId", 11L);
        parameters.put("knowledgeId", 101L);
        parameters.put("visibleUserId", 7L);
        parameters.put("limit", 20);
        parameters.put("offset", 0L);
        String sql = normalize(configuration.getMappedStatement(namespace + "selectQuestions")
            .getBoundSql(parameters).getSql());

        assertThat(sql)
            .contains("row_number() over")
            .contains("with recursive descendants")
            .contains("es.certification_id=?")
            .contains("left join lateral")
            .contains("on true")
            .contains("exists")
            .contains("order by case rr.status when 'rejected' then 1 when 'pending_review' then 2 when 'published' then 3 when 'draft' then 4 else 5 end, rr.update_time desc")
            .contains("limit ? offset ?")
            .doesNotContain("rr.answer")
            .doesNotContain("rr.analysis")
            .doesNotContain("option_text");
    }

    @Test
    void jointSubmitLocksQuestionRevisionsInStableOrderAndVisibilityScope() throws Exception {
        Configuration configuration = configuration();
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.QuestionMapper.lockReviewRevisions")
            .getBoundSql(Map.of("revisionIds", java.util.List.of(3L, 7L), "visibleUserId", 9L)).getSql());
        assertThat(sql).contains("r.id in (").contains("q.create_by=?")
            .contains("order by r.id for update of q,r");
    }

    @Test
    void saveAndDeleteStatementsUseOptimisticLockAndLogicalDelete() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.QuestionMapper.";
        Map<String, Object> update = new HashMap<>();
        update.put("revisionId", 2L); update.put("rowVersion", 3L); update.put("status", "draft");
        update.put("questionType", "CHOICE"); update.put("difficulty", "easy"); update.put("estimatedSeconds", 60);
        update.put("stem", "题干"); update.put("answer", "{\"schema_version\":\"1.0\"}");
        update.put("analysis", null); update.put("commonMistakes", null); update.put("contentHash", "hash"); update.put("userId", 1L);
        String updateSql = normalize(configuration.getMappedStatement(namespace + "updateRevision").getBoundSql(update).getSql());
        assertThat(updateSql).contains("row_version=row_version+1").contains("where id=? and row_version=?");

        String deleteSql = normalize(configuration.getMappedStatement(namespace + "softDeleteQuestion")
            .getBoundSql(Map.of("questionId", 1L, "userId", 7L)).getSql());
        assertThat(deleteSql).contains("set del_flag='1'").contains("and del_flag='0'");
    }

    @Test
    void reviewStatementsLockPublishDirectlyAndScopeIdempotency() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.QuestionMapper.";
        String approveSql = normalize(configuration.getMappedStatement(namespace + "markReviewPublished")
            .getBoundSql(Map.of("revisionId", 2L, "userId", 7L)).getSql());
        String rejectSql = normalize(configuration.getMappedStatement(namespace + "markReviewRejected")
            .getBoundSql(Map.of("revisionId", 2L, "userId", 7L, "opinion", "依据不足")).getSql());
        String idempotencySql = normalize(configuration.getMappedStatement(namespace + "insertReviewIdempotency")
            .getBoundSql(Map.of("id", 1L, "action", "approve_question_revision", "requestId", "r-1",
                "payloadHash", "hash", "questionId", 3L, "expiresTime", java.time.OffsetDateTime.now())).getSql());
        String submitSql = normalize(configuration.getMappedStatement(namespace + "markReviewSubmitted")
            .getBoundSql(Map.of("revisionId", 2L, "userId", 7L)).getSql());
        BoundSql submitIdempotencyBoundSql = configuration.getMappedStatement(namespace + "insertIdempotency")
            .getBoundSql(Map.of("id", 1L, "action", "submit_review", "requestId", "r-2", "payloadHash", "hash",
                "resourceType", "question_revision", "resourceId", 2L, "expiresTime", java.time.OffsetDateTime.now()));
        String submitIdempotencySql = normalize(submitIdempotencyBoundSql.getSql());
        String offlineSql = normalize(configuration.getMappedStatement(namespace + "markPublishedDraft")
            .getBoundSql(Map.of("revisionId", 2L, "userId", 7L)).getSql());

        assertThat(approveSql).contains("status='published'").contains("and status='pending_review'")
            .contains("row_version=row_version+1").contains("reviewed_by=?", "published_by=?");
        assertThat(rejectSql).contains("status='rejected'").contains("review_opinion=?")
            .contains("and status='pending_review'");
        assertThat(submitSql).contains("status='pending_review'").contains("and status in ('draft','rejected')")
            .contains("row_version=row_version+1");
        assertThat(idempotencySql).contains("on conflict do nothing");
        assertThat(submitIdempotencySql).contains("on conflict (action_code,request_id) do nothing");
        assertThat(submitIdempotencyBoundSql.getParameterMappings())
            .extracting(parameterMapping -> parameterMapping.getProperty())
            .contains("resourceType", "resourceId");
        assertThat(offlineSql).contains("status='draft'").contains("and status='published'")
            .contains("published_by=null").contains("published_time=null");
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/QuestionMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
