package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.QualificationRow;
import org.dromara.certmuse.catalog.domain.ReferenceBlockerRow;
import org.dromara.certmuse.catalog.domain.ReferenceCountRow;
import org.dromara.certmuse.catalog.domain.SyllabusVersionRow;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.domain.vo.QualificationVo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionVo;
import org.dromara.certmuse.catalog.mapper.QualificationVersionMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.QualificationVersionServiceImpl;
import org.dromara.certmuse.catalog.support.QualificationVersionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class QualificationVersionServiceTest {
    private final QualificationVersionMapper mapper = mock(QualificationVersionMapper.class);
    private final ImportPersistenceService importPersistence = mock(ImportPersistenceService.class);
    private final QualificationVersionService service = new QualificationVersionServiceImpl(mapper, JsonMapper.builder().build(), importPersistence);

    @Test
    void rejectsInvalidFiltersBeforeExecutingSql() {
        QualificationQueryBo query = new QualificationQueryBo(); query.setQualificationLevel("high");
        assertThatThrownBy(() -> service.list(query)).isInstanceOf(QualificationVersionException.class)
            .extracting("status").isEqualTo(400);
        verify(mapper, never()).countQualifications(any());
    }

    @Test
    void paginatesQualificationsThenLoadsVersionsAndCountersInBatches() {
        QualificationQueryBo query = new QualificationQueryBo(); query.setPageNum(2); query.setPageSize(20); query.setKeyword("架构");
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.countQualifications(any())).thenReturn(1L);
        when(mapper.selectQualifications(any(), anyInt(), anyLong())).thenReturn(List.of(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time)));
        when(mapper.selectVersions(any())).thenReturn(List.of(new SyllabusVersionRow(2L, 1L, "第二版", LocalDate.parse("2024-05-01"), time, time)));
        when(mapper.selectQualificationReferenceCounts(any())).thenReturn(List.of(new ReferenceCountRow(1L, 3L)));
        when(mapper.selectVersionReferenceCounts(any())).thenReturn(List.of(new ReferenceCountRow(2L, 4L)));

        var result = service.list(query);

        assertThat(result.getTotal()).isEqualTo(1); assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.referenceCount()).isEqualTo(3); assertThat(row.versions()).singleElement().satisfies(version -> assertThat(version.referenceCount()).isEqualTo(4));
        });
        verify(mapper).selectQualifications(any(), anyInt(), org.mockito.ArgumentMatchers.eq(20L));
        verify(mapper).selectVersions(any()); verify(mapper).selectQualificationReferenceCounts(any()); verify(mapper).selectVersionReferenceCounts(any());
    }

    @Test
    void returnsEmptyRowsForAnOutOfRangePageWithoutBuildingEmptyInQueries() {
        QualificationQueryBo query = new QualificationQueryBo(); query.setPageNum(2); query.setPageSize(20);
        when(mapper.countQualifications(any())).thenReturn(1L);
        when(mapper.selectQualifications(any(), anyInt(), anyLong())).thenReturn(List.of());

        var result = service.list(query);

        assertThat(result.getTotal()).isEqualTo(1); assertThat(result.getRows()).isEmpty();
        verify(mapper, never()).selectVersions(any());
        verify(mapper, never()).selectVersionReferenceCounts(any());
    }

    @Test
    void listsQualificationsWithoutVersionsWithoutBuildingAnEmptyVersionReferenceQuery() {
        QualificationQueryBo query = new QualificationQueryBo();
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.countQualifications(any())).thenReturn(1L);
        when(mapper.selectQualifications(any(), anyInt(), anyLong())).thenReturn(List.of(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time)));
        when(mapper.selectVersions(any())).thenReturn(List.of());
        when(mapper.selectQualificationReferenceCounts(any())).thenReturn(List.of(new ReferenceCountRow(1L, 3L)));

        QualificationVo result = service.list(query).getRows().iterator().next();

        assertThat(result.versionCount()).isZero(); assertThat(result.referenceCount()).isEqualTo(3);
        verify(mapper, never()).selectVersionReferenceCounts(any());
    }

    @Test
    void returnsCurrentNestedVersionsAndReferenceCountsAfterQualificationUpdate() {
        QualificationWriteBo command = qualification("ARCH", "架构", "HIGH", "0", 1);
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        QualificationRow row = new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time);
        when(mapper.lockQualification(1L)).thenReturn(row);
        when(mapper.selectQualification(anyLong())).thenReturn(row);
        when(mapper.selectVersions(any())).thenReturn(List.of(new SyllabusVersionRow(2L, 1L, "第二版", LocalDate.parse("2024-05-01"), time, time)));
        when(mapper.selectQualificationReferenceCounts(any())).thenReturn(List.of(new ReferenceCountRow(1L, 3L)));
        when(mapper.selectVersionReferenceCounts(any())).thenReturn(List.of(new ReferenceCountRow(2L, 4L)));

        QualificationVo result = service.updateQualification("1", "d89b5981-37df-4336-87b1-1300b5c3455a", command);

        assertThat(result.versionCount()).isEqualTo(1); assertThat(result.referenceCount()).isEqualTo(3);
        assertThat(result.versions()).singleElement().satisfies(version -> assertThat(version.referenceCount()).isEqualTo(4));
    }

    @Test
    void rejectsDuplicateCodeBeforeCreatingQualification() {
        when(mapper.findQualificationIdByCode("ARCH", null)).thenReturn(2L);

        assertThatThrownBy(() -> service.createQualification("d89b5981-37df-4336-87b1-1300b5c3455a", qualification("ARCH", "架构师", "HIGH", "0", 1)))
            .isInstanceOf(QualificationVersionException.class)
            .satisfies(exception -> assertThat(((QualificationVersionException) exception).data().errorCode()).isEqualTo("QUALIFICATION_CODE_CONFLICT"));
        verify(mapper, never()).insertQualification(anyLong(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    void createsTheSharedThreeSubjectsWithoutCreatingASyllabusWithANewHighQualification() {
        QualificationWriteBo command = qualification("ARCH", "架构", "HIGH", "0", 1);
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        QualificationRow row = new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time);
        when(mapper.selectQualification(anyLong())).thenReturn(row);
        when(mapper.selectVersions(any())).thenReturn(List.of());
        when(mapper.selectQualificationReferenceCounts(any())).thenReturn(List.of());

        service.createQualification("d89b5981-37df-4336-87b1-1300b5c3455a", command);

        verify(mapper).insertExamSubject(anyLong(), anyLong(), eq("COMPREHENSIVE"), eq("综合知识"), any());
        verify(mapper).insertExamSubject(anyLong(), anyLong(), eq("CASE_ANALYSIS"), eq("案例分析"), any());
        verify(mapper).insertExamSubject(anyLong(), anyLong(), eq("ESSAY"), eq("论文"), any());
        verify(mapper, never()).insertVersion(anyLong(), anyLong(), any(), isNull(), any(), any());
    }

    @Test
    void rejectsDuplicateNameBeforeUpdatingQualification() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockQualification(1L)).thenReturn(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time));
        when(mapper.findQualificationIdByName("架构师", 1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.updateQualification("1", "d89b5981-37df-4336-87b1-1300b5c3455a", qualification("NEW_ARCH", "架构师", "HIGH", "0", 1)))
            .isInstanceOf(QualificationVersionException.class)
            .satisfies(exception -> assertThat(((QualificationVersionException) exception).data().errorCode()).isEqualTo("QUALIFICATION_NAME_CONFLICT"));
        verify(mapper, never()).updateQualification(anyLong(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void replaysTheStoredCreateResponseInsteadOfReadingTheCurrentQualification() throws Exception {
        QualificationWriteBo command = qualification("ARCH", "架构", "HIGH", "0", 1);
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        QualificationVo expected = new QualificationVo("1", "ARCH", "创建时名称", "HIGH", "0", 1, 0, 0, time, time, List.of());
        String payload = JsonMapper.builder().build().writeValueAsString(Map.of("code", "ARCH", "name", "架构", "level", "HIGH", "status", "0", "sortOrder", 1));
        String response = JsonMapper.builder().build().writeValueAsString(Map.of("schema_version", "qualification_version_response/1.0", "data", expected));
        when(mapper.selectIdempotency(any(), any())).thenReturn(new CmIdempotencyRecord(DigestUtil.sha256Hex(payload), "succeeded", 1L, 200, response));

        QualificationVo result = service.createQualification("d89b5981-37df-4336-87b1-1300b5c3455a", command);

        assertThat(result.id()).isEqualTo(expected.id()); assertThat(result.certificationName()).isEqualTo(expected.certificationName());
        assertThat(result.versionCount()).isZero(); assertThat(result.createTime().toInstant()).isEqualTo(expected.createTime().toInstant());
        assertThat(result.updateTime().toInstant()).isEqualTo(expected.updateTime().toInstant());
        verify(mapper, never()).selectQualification(anyLong());
    }

    @Test
    void deletesVersionWithContentWithoutRunningReferenceBlockerChecks() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockVersion(1L, 2L)).thenReturn(new SyllabusVersionRow(2L, 1L, "第二版", LocalDate.parse("2024-05-01"), time, time));

        service.deleteVersion("1", "2", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(mapper).deleteVersion(2L, 1L);
        verify(mapper, never()).selectVersionBlockers(anyLong());
    }

    @Test
    void deletesQualificationWithoutBlockingOnBusinessReferences() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockQualification(1L)).thenReturn(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time));

        service.deleteQualification("1", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(mapper).deleteQualification(1L);
        verify(mapper, never()).selectQualificationBlockers(anyLong());
    }

    @Test
    void deletesVersionWithoutReferencesAndStoresANullIdempotencyResponseData() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockVersion(1L, 2L)).thenReturn(new SyllabusVersionRow(2L, 1L, "第二版", LocalDate.parse("2024-05-01"), time, time));
        service.deleteVersion("1", "2", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(mapper).deleteVersion(2L, 1L);
        verify(mapper).completeIdempotency(org.mockito.ArgumentMatchers.eq("syllabus_version_delete"),
            org.mockito.ArgumentMatchers.eq("d89b5981-37df-4336-87b1-1300b5c3455a"),
            org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.argThat(response -> response.contains("\"data\":null")));
    }

    @Test
    void deletesQualificationThroughTheDatabaseCascade() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockQualification(1L)).thenReturn(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time));

        service.deleteQualification("1", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(mapper).deleteQualification(1L);
        var ordered = inOrder(mapper);
        ordered.verify(mapper).setQualificationCascadeDelete(true);
        ordered.verify(mapper).deleteQualification(1L);
        ordered.verify(mapper).setQualificationCascadeDelete(false);
        verify(mapper).completeIdempotency(org.mockito.ArgumentMatchers.eq("qualification_delete"),
            org.mockito.ArgumentMatchers.eq("d89b5981-37df-4336-87b1-1300b5c3455a"),
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.argThat(response -> response.contains("\"data\":null")));
    }

    @Test
    void enqueuesQualificationScopedSourceFilesAndImagesForPostCommitCleanup() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockQualification(1L)).thenReturn(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time));
        when(mapper.selectSourceObjectKeysByQualification(1L)).thenReturn(List.of(
            "imports/question/source.zip", "imports/textbook/source.jsonl"
        ));
        when(mapper.selectImageObjectKeysByQualification(1L)).thenReturn(List.of(
            "question-images/only.png", "shared/image.png"
        ));
        service.deleteQualification("1", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(importPersistence).enqueueCleanup("imports/question/source.zip", "unknown", "qualification_version_delete");
        verify(importPersistence).enqueueCleanup("imports/textbook/source.jsonl", "unknown", "qualification_version_delete");
        verify(importPersistence).enqueueImageCleanup("question-images/only.png", "unknown", "qualification_version_delete");
        verify(importPersistence).enqueueImageCleanup("shared/image.png", "unknown", "qualification_version_delete");
    }

    @Test
    void enqueuesVersionSourceObjectCleanupAfterDeletingVersion() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        when(mapper.lockVersion(1L, 2L)).thenReturn(new SyllabusVersionRow(2L, 1L, "第二版", LocalDate.parse("2024-05-01"), time, time));
        when(mapper.selectSourceObjectKeysByVersion(2L)).thenReturn(List.of("imports/knowledge/source.jsonl"));
        service.deleteVersion("1", "2", "d89b5981-37df-4336-87b1-1300b5c3455a");

        verify(importPersistence).enqueueCleanup("imports/knowledge/source.jsonl", "unknown", "qualification_version_delete");
    }

    @Test
    void createsAndUpdatesSyllabusVersionsWithReferenceCounts() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        QualificationRow qualification = new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time);
        SyllabusVersionWriteBo command = version("第二版", LocalDate.parse("2026-01-01"));
        when(mapper.lockQualification(1L)).thenReturn(qualification);
        when(mapper.lockVersion(1L, 2L)).thenReturn(new SyllabusVersionRow(2L, 1L, "旧版本", null, time, time));
        when(mapper.selectVersion(anyLong(), anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(1);
            return new SyllabusVersionRow(id, 1L, "第二版", LocalDate.parse("2026-01-01"), time, time);
        });
        when(mapper.selectVersionReferenceCounts(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Long> ids = invocation.getArgument(0);
            return List.of(new ReferenceCountRow(ids.get(0), 4L));
        });

        SyllabusVersionVo created = service.createVersion("1", "d89b5981-37df-4336-87b1-1300b5c3455a", command);
        SyllabusVersionVo updated = service.updateVersion("1", "2", "e89b5981-37df-4336-87b1-1300b5c3455a", command);

        assertThat(created.versionName()).isEqualTo("第二版");
        assertThat(created.referenceCount()).isEqualTo(4);
        assertThat(updated.id()).isEqualTo("2");
        assertThat(updated.referenceCount()).isEqualTo(4);
        verify(mapper).insertVersion(anyLong(), eq(1L), eq("第二版"), eq(LocalDate.parse("2026-01-01")), any(), any());
        verify(mapper).updateVersion(eq(2L), eq(1L), eq("第二版"), eq(LocalDate.parse("2026-01-01")), any());
    }

    @Test
    void translatesVersionNameUniquenessAndDeletionBlockers() {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");
        SyllabusVersionWriteBo command = version("第二版", null);
        when(mapper.lockQualification(1L)).thenReturn(new QualificationRow(1L, "ARCH", "架构", "HIGH", "0", 1, time, time));
        doThrow(new DataIntegrityViolationException("duplicate"))
            .when(mapper).insertVersion(anyLong(), anyLong(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.createVersion("1", "d89b5981-37df-4336-87b1-1300b5c3455a", command))
            .isInstanceOf(QualificationVersionException.class)
            .satisfies(error -> assertThat(((QualificationVersionException) error).data().errorCode()).isEqualTo("SYLLABUS_VERSION_NAME_CONFLICT"));

        when(mapper.lockVersion(1L, 2L)).thenReturn(new SyllabusVersionRow(2L, 1L, "第二版", null, time, time));
        when(mapper.selectVersionDeleteBlockers(2L)).thenReturn(List.of(new ReferenceBlockerRow("question", "题目", 3L)));
        assertThatThrownBy(() -> service.deleteVersion("1", "2", "e89b5981-37df-4336-87b1-1300b5c3455a"))
            .isInstanceOf(QualificationVersionException.class)
            .satisfies(error -> {
                QualificationVersionException exception = (QualificationVersionException) error;
                assertThat(exception.data().errorCode()).isEqualTo("SYLLABUS_VERSION_DELETE_BLOCKED");
                assertThat(exception.data().blockers()).singleElement().satisfies(blocker -> assertThat(blocker.count()).isEqualTo(3));
            });
        verify(mapper, never()).deleteVersion(anyLong(), anyLong());
    }

    @Test
    void rejectsMissingQualificationVersionAndInvalidVersionCommands() {
        SyllabusVersionWriteBo command = version("第二版", null);
        when(mapper.lockQualification(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.createVersion("1", "d89b5981-37df-4336-87b1-1300b5c3455a", command))
            .isInstanceOf(QualificationVersionException.class)
            .satisfies(error -> assertThat(((QualificationVersionException) error).data().errorCode()).isEqualTo("QUALIFICATION_NOT_FOUND"));

        assertThatThrownBy(() -> service.createVersion("0", "d89b5981-37df-4336-87b1-1300b5c3455a", command))
            .isInstanceOf(QualificationVersionException.class);
        SyllabusVersionWriteBo invalid = version(" ", null);
        assertThatThrownBy(() -> service.createVersion("1", "d89b5981-37df-4336-87b1-1300b5c3455a", invalid))
            .isInstanceOf(QualificationVersionException.class);
    }

    private static SyllabusVersionWriteBo version(String name, LocalDate publishedDate) {
        SyllabusVersionWriteBo command = new SyllabusVersionWriteBo();
        command.setVersionName(name);
        command.setPublishedDate(publishedDate);
        return command;
    }

    private static QualificationWriteBo qualification(String code, String name, String level, String status, int sortOrder) {
        QualificationWriteBo command = new QualificationWriteBo();
        command.setCertificationCode(code); command.setCertificationName(name); command.setQualificationLevel(level);
        command.setStatus(status); command.setSortOrder(sortOrder); return command;
    }
}
