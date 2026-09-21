package org.dromara.certmuse.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class QualificationVersionMapperXmlTest {
    @Test
    void parsesM01StatementsAndUsesBatchQueriesAndControlledFilters() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.catalog.mapper.QualificationVersionMapper.";
        for (String statement : java.util.List.of("selectQualifications", "countQualifications", "selectVersions",
            "selectQualificationReferenceCounts", "selectVersionReferenceCounts", "lockQualification", "lockVersion",
            "selectQualificationBlockers", "selectVersionBlockers", "selectIdempotency", "insertExamSubject",
            "setQualificationCascadeDelete",
            "selectSourceObjectKeysByVersion", "selectImageObjectKeysByVersion", "selectSourceObjectKeysByQualification",
            "selectImageObjectKeysByQualification", "countImageObjectKeyReferences", "completeIdempotency")) {
            assertThat(configuration.hasStatement(namespace + statement)).isTrue();
        }
        QualificationQueryBo query = new QualificationQueryBo(); query.setKeyword("架构"); query.setQualificationLevel("HIGH"); query.setStatus("0");
        String listSql = sql(configuration, namespace + "selectQualifications", Map.of("query", query, "limit", 20, "offset", 0L));
        assertThat(listSql).contains("exists (select 1 from cm_syllabus_version")
            .contains("order by ec.sort_order asc, ec.certification_name asc, ec.id asc").doesNotContain("${");
        String versionsSql = sql(configuration, namespace + "selectVersions", Map.of("certificationIds", java.util.List.of(1L, 2L)));
        assertThat(versionsSql).contains("certification_id in").contains("nulls last");
        String blockersSql = sql(configuration, namespace + "selectVersionBlockers", Map.of("id", 1L));
        assertThat(blockersSql).contains("knowledge_prerequisite").contains("cm_document").contains("reference_count")
            .doesNotContain("textbook_import_batch");
        String versionSourceSql = sql(configuration, namespace + "selectSourceObjectKeysByVersion", Map.of("syllabusVersionId", 1L));
        assertThat(versionSourceSql).contains("left join cm_document").contains("document.syllabus_version_id");
        String qualificationSourceSql = sql(configuration, namespace + "selectSourceObjectKeysByQualification", Map.of("certificationId", 1L));
        assertThat(qualificationSourceSql)
            .contains("batch.certification_id")
            .contains("document.certification_id")
            .contains("document.source_file_path")
            .contains("document_version.certification_id");
        String qualificationImageSql = sql(configuration, namespace + "selectImageObjectKeysByQualification", Map.of("certificationId", 1L));
        assertThat(qualificationImageSql)
            .contains("cm_document_image")
            .contains("cm_question_image")
            .contains("subject.certification_id");
        String imageReferenceSql = sql(configuration, namespace + "countImageObjectKeyReferences", Map.of("objectKey", "shared.png"));
        assertThat(imageReferenceSql).contains("cm_document_image").contains("cm_question_image");
        String cascadeFlagSql = sql(configuration, namespace + "setQualificationCascadeDelete", Map.of("enabled", true));
        assertThat(cascadeFlagSql).contains("set_config('certmuse.qualification_cascade_delete'").contains("true");
    }

    @Test
    void omitsNullableQualificationIdExclusionForCreateAndKeepsItForUpdate() throws Exception {
        Configuration configuration = configuration();
        String namespace = "org.dromara.certmuse.catalog.mapper.QualificationVersionMapper.";
        for (String statement : java.util.List.of("findQualificationIdByCode", "findQualificationIdByName")) {
            Map<String, Object> createParameters = new HashMap<>();
            createParameters.put(statement.endsWith("Code") ? "code" : "name", "ARCH");
            createParameters.put("excludeId", null);
            assertThat(sql(configuration, namespace + statement, createParameters))
                .doesNotContain("? is null")
                .doesNotContain("id <> ?");

            Map<String, Object> updateParameters = new HashMap<>(createParameters);
            updateParameters.put("excludeId", 1L);
            assertThat(sql(configuration, namespace + statement, updateParameters))
                .contains("id <> ?")
                .doesNotContain("? is null");
        }
    }

    private static Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/certmuse/QualificationVersionMapper.xml";
        try (InputStream stream = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(stream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private static String sql(Configuration configuration, String statement, Object parameters) {
        return configuration.getMappedStatement(statement).getBoundSql(parameters).getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
