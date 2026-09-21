package org.dromara.certmuse.question.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionListRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class CollectionMapperXmlTest {
    @Test
    void parsesAllCollectionStatements() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.CollectionMapper.";
        assertThat(configuration.hasStatement(namespace + "selectCollections")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectManagedCollectionIds")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectManagedCollectionRevisions")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockCollection")).isTrue();
        assertThat(configuration.hasStatement(namespace + "updateCollectionName")).isTrue();
        assertThat(configuration.hasStatement(namespace + "updateDraft")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markPublished")).isTrue();
        assertThat(configuration.hasStatement(namespace + "upsertCurrent")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockCurrentPublished")).isTrue();
        assertThat(configuration.hasStatement(namespace + "lockPendingRevisionsByQuestionRevision")).isTrue();
        assertThat(configuration.hasStatement(namespace + "deleteCurrent")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insertAudit")).isTrue();
    }

    @Test
    void mapsStatementsToDedicatedPersistenceRows() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.CollectionMapper.";
        assertThat(configuration.getMappedStatement(namespace + "selectCollections").getResultMaps().getFirst().getType())
            .isEqualTo(CollectionListRow.class);
        assertThat(configuration.getMappedStatement(namespace + "selectRevision").getResultMaps().getFirst().getType())
            .isEqualTo(CollectionRevisionRow.class);
        assertThat(configuration.getMappedStatement(namespace + "selectItems").getResultMaps().getFirst().getType())
            .isEqualTo(CollectionItemRow.class);
        assertThat(configuration.getMappedStatement(namespace + "selectIdempotency").getResultMaps().getFirst().getType())
            .isEqualTo(CollectionIdempotencyRow.class);
    }

    @Test
    void listReturnsEveryRevisionInContractOrder() throws Exception {
        Configuration configuration = configuration();
        CollectionQueryBo query = new CollectionQueryBo();
        query.setKeyword("诊断");
        query.setStatus("draft");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query);
        parameters.put("certificationId", 1L);
        parameters.put("syllabusVersionId", 2L);
        parameters.put("limit", 20);
        parameters.put("offset", 0L);
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.selectCollections")
            .getBoundSql(parameters).getSql());
        assertThat(sql).contains("cm_collection_revision r")
            .contains("r.status")
            .contains("r.status='rejected'")
            .contains("r.status='published' and cur.collection_revision_id=r.id")
            .contains("cm_collection_current").contains("r.status=?")
            .doesNotContain("c.status=");
    }

    @Test
    void managementListPaginatesCollectionsAndFiltersDisplayStatuses() throws Exception {
        Configuration configuration = configuration();
        CollectionQueryBo query = new CollectionQueryBo();
        query.setKeyword("父题集");
        query.setStatuses(java.util.List.of("rejected", "published"));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("query", query); parameters.put("certificationId", null);
        parameters.put("syllabusVersionId", null); parameters.put("limit", 20); parameters.put("offset", 0L);
        String idsSql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.selectManagedCollectionIds")
            .getBoundSql(parameters).getSql());
        assertThat(idsSql).contains("group by c.id").contains("r.status='rejected'")
            .contains("c.collection_name ilike").contains("greatest(max(c.update_time), max(r.update_time))")
            .contains("limit ? offset ?").doesNotContain("cm_collection_current cur")
            .doesNotContain("r.collection_name ilike");
        String rowsSql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.selectManagedCollectionRevisions")
            .getBoundSql(Map.of("collectionIds", java.util.List.of(1L, 2L))).getSql());
        assertThat(rowsSql).contains("cm_collection_current cur").contains("array_position")
            .contains("c.collection_name parent_collection_name")
            .contains("c.update_time collection_updated_time")
            .contains("r.review_opinion");
    }

    @Test
    void parentRenameUpdatesOnlyTheStableCollectionTable() throws Exception {
        Configuration configuration = configuration();
        String lockSql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.lockDraftRevisionForRename")
            .getBoundSql(Map.of("collectionId", 10L)).getSql());
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.updateCollectionName")
            .getBoundSql(Map.of("collectionId", 10L, "name", "新名称")).getSql());

        assertThat(lockSql).contains("cm_collection_revision")
            .contains("collection_id=? and status='draft'")
            .contains("limit 1 for update");
        assertThat(sql).contains("update cm_collection set collection_name=?")
            .contains("update_time=now()")
            .doesNotContain("cm_collection_revision");
    }

    @Test
    void draftUpdateUsesStatusAndOptimisticLock() throws Exception {
        Configuration configuration = configuration();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("revisionId", 3L); parameters.put("rowVersion", 2L);
        parameters.put("name", "题集"); parameters.put("type", "PRACTICE");
        parameters.put("certificationId", 1L); parameters.put("syllabusVersionId", 2L);
        parameters.put("durationMinutes", 60); parameters.put("questionCount", 2);
        parameters.put("totalScore", 4);
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.updateDraft")
            .getBoundSql(parameters).getSql());
        assertThat(sql).contains("row_version=row_version+1")
            .contains("status in ('draft','rejected')").contains("row_version=?")
            .doesNotContain("review_opinion=null");
    }

    @Test
    void reverseLookupLocksPendingCollectionsInStableOrder() throws Exception {
        Configuration configuration = configuration();
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.lockPendingRevisionsByQuestionRevision")
            .getBoundSql(Map.of("questionRevisionId", 7L)).getSql());
        assertThat(sql).contains("i.question_revision_id=?")
            .contains("r.status='pending_review'").contains("order by r.id for update of r");
    }

    @Test
    void questionMetadataExcludesLogicallyDeletedQuestions() throws Exception {
        Configuration configuration = configuration();
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.selectQuestionRevisionMetadata")
            .getBoundSql(Map.of("ids", java.util.List.of(21L), "visibleUserId", 7L)).getSql());
        assertThat(sql).contains("q.del_flag='0'");
    }

    @Test
    void activeSubjectsUseTheActualExamSubjectSchema() throws Exception {
        Configuration configuration = configuration();
        String sql = normalize(configuration.getMappedStatement(
            "org.dromara.certmuse.question.mapper.CollectionMapper.selectActiveSubjectIds")
            .getBoundSql(Map.of("certificationId", 1L)).getSql());
        assertThat(sql).isEqualTo("select id from cm_exam_subject where certification_id=? order by id")
            .doesNotContain("status");
    }

    @Test
    void reviewTransitionsIncrementRowVersionAndUseExpectedStates() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.question.mapper.CollectionMapper.";
        for (String statement : java.util.List.of("markSubmitted", "markPublished", "markRejected")) {
            String sql = normalize(configuration.getMappedStatement(namespace + statement)
                .getBoundSql(Map.of("revisionId", 3L, "userId", 1L, "opinion", "需修改")).getSql());
            assertThat(sql).contains("row_version=row_version+1").contains("where id=? and status");
        }
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/CollectionMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
