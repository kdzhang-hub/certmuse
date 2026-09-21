package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.hutool.crypto.digest.DigestUtil;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.dromara.certmuse.catalog.domain.CmIdempotencyRecord;
import org.dromara.certmuse.catalog.domain.QualificationRow;
import org.dromara.certmuse.catalog.domain.SyllabusVersionRow;
import org.dromara.certmuse.catalog.domain.bo.QualificationQueryBo;
import org.dromara.certmuse.catalog.domain.bo.QualificationWriteBo;
import org.dromara.certmuse.catalog.domain.bo.SyllabusVersionWriteBo;
import org.dromara.certmuse.catalog.domain.vo.SyllabusVersionVo;
import org.dromara.certmuse.catalog.mapper.QualificationVersionMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.dromara.certmuse.catalog.service.impl.QualificationVersionServiceImpl;
import org.dromara.certmuse.catalog.support.QualificationVersionException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class QualificationVersionServicePublicBranchTest {

    private static final String REQUEST_ID = "d89b5981-37df-4336-87b1-1300b5c3455a";
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-08-04T10:30:00+08:00");

    @Mock
    private QualificationVersionMapper mapper;
    @Mock
    private ImportPersistenceService importPersistence;

    private JsonMapper jsonMapper;
    private QualificationVersionService service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new QualificationVersionServiceImpl(mapper, jsonMapper, importPersistence);
    }

    @Test
    void rejectsEveryInvalidQualificationListFilterBeforeReadingTheDatabase() {
        assertFailure(() -> service.list(null), 400, "QUALIFICATION_VERSION_INVALID");
        assertInvalidList(query -> query.setKeyword("x".repeat(101)));
        assertInvalidList(query -> query.setQualificationLevel("high"));
        assertInvalidList(query -> query.setStatus("2"));
        assertInvalidList(query -> query.setPageNum(0));
        assertInvalidList(query -> query.setPageSize(0));
        assertInvalidList(query -> query.setPageSize(101));

        verify(mapper, never()).countQualifications(any());
    }

    @Test
    void returnsTheEmptyFirstPageWithoutIssuingChildQueries() {
        QualificationQueryBo query = new QualificationQueryBo();
        query.setKeyword("   ");
        when(mapper.countQualifications(query)).thenReturn(0L);

        var result = service.list(query);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRows()).isEmpty();
        assertThat(query.getKeyword()).isNull();
        verify(mapper, never()).selectQualifications(any(), anyInt(), anyLong());
        verify(mapper, never()).selectVersions(any());
    }

    @Test
    void ordersCurrentVersionsByDateCreationTimeAndIdentifierWithUndatedVersionsLast() {
        QualificationQueryBo query = new QualificationQueryBo();
        QualificationRow qualification = qualificationRow(1L, "HIGH");
        OffsetDateTime earlier = CREATED_AT.minusDays(1);
        LocalDate publishedDate = LocalDate.parse("2026-01-01");
        when(mapper.countQualifications(query)).thenReturn(1L);
        when(mapper.selectQualifications(query, 20, 0L)).thenReturn(List.of(qualification));
        when(mapper.selectVersions(List.of(1L))).thenReturn(List.of(
            versionRow(2L, publishedDate, earlier),
            versionRow(3L, publishedDate, CREATED_AT),
            versionRow(4L, publishedDate, CREATED_AT),
            versionRow(5L, null, CREATED_AT.plusDays(1))
        ));
        when(mapper.selectQualificationReferenceCounts(List.of(1L))).thenReturn(List.of());
        when(mapper.selectVersionReferenceCounts(List.of(2L, 3L, 4L, 5L))).thenReturn(List.of());

        var result = service.list(query);

        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.referenceCount()).isZero();
            assertThat(row.versions()).extracting(SyllabusVersionVo::id)
                .containsExactly("4", "3", "2", "5");
            assertThat(row.versions()).allSatisfy(version -> assertThat(version.referenceCount()).isZero());
        });
    }

    @Test
    void rejectsInvalidQualificationCommandsAndRequestIdsBeforeWriting() {
        assertFailure(() -> service.createQualification(REQUEST_ID, null), 400, "QUALIFICATION_VERSION_INVALID");
        assertInvalidQualification(command -> command.setCertificationCode(null));
        assertInvalidQualification(command -> command.setCertificationCode("   "));
        assertInvalidQualification(command -> command.setCertificationCode("bad-code"));
        assertInvalidQualification(command -> command.setCertificationCode("x".repeat(51)));
        assertInvalidQualification(command -> command.setCertificationName(null));
        assertInvalidQualification(command -> command.setCertificationName("x".repeat(201)));
        assertInvalidQualification(command -> command.setQualificationLevel(null));
        assertInvalidQualification(command -> command.setQualificationLevel("UNKNOWN"));
        assertInvalidQualification(command -> command.setStatus("2"));
        assertInvalidQualification(command -> command.setSortOrder(null));
        assertInvalidQualification(command -> command.setSortOrder(-1));
        assertFailure(
            () -> service.createQualification("not-a-uuid", qualification("ARCH", "架构", "HIGH", "0", 1)),
            400,
            "QUALIFICATION_VERSION_INVALID"
        );

        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsInvalidVersionCommandsIdentifiersAndRequestIdsBeforeWriting() {
        SyllabusVersionWriteBo valid = version("第二版", LocalDate.parse("2026-01-01"));
        assertFailure(() -> service.createVersion("0", REQUEST_ID, valid), 400, "QUALIFICATION_VERSION_INVALID");
        assertFailure(() -> service.createVersion("not-an-id", REQUEST_ID, valid), 400, "QUALIFICATION_VERSION_INVALID");
        assertFailure(() -> service.updateVersion("1", "0", REQUEST_ID, valid), 400, "QUALIFICATION_VERSION_INVALID");
        assertFailure(() -> service.createVersion("1", REQUEST_ID, null), 400, "QUALIFICATION_VERSION_INVALID");
        assertInvalidVersion(command -> command.setVersionName(null));
        assertInvalidVersion(command -> command.setVersionName("   "));
        assertInvalidVersion(command -> command.setVersionName("x".repeat(201)));
        assertFailure(
            () -> service.createVersion("1", "not-a-uuid", valid),
            400,
            "QUALIFICATION_VERSION_INVALID"
        );

        verifyNoInteractions(mapper);
    }

    @Test
    void createsANormalizedLowLevelQualificationWithoutHighLevelExamSubjects() {
        when(mapper.findQualificationIdByCode("ARCH", null)).thenReturn(0L);
        when(mapper.findQualificationIdByName("架构师", null)).thenReturn(0L);
        when(mapper.selectQualification(anyLong())).thenAnswer(invocation ->
            new QualificationRow(invocation.getArgument(0), "ARCH", "架构师", "LOW", "0", 3, CREATED_AT, CREATED_AT));
        when(mapper.selectVersions(any())).thenReturn(List.of());
        when(mapper.selectQualificationReferenceCounts(any())).thenReturn(List.of());

        try (MockedStatic<LoginHelper> login = login()) {
            var result = service.createQualification(
                REQUEST_ID,
                qualification(" arch ", "  架构师  ", "LOW", "0", 3)
            );

            assertThat(result.certificationCode()).isEqualTo("ARCH");
            assertThat(result.certificationName()).isEqualTo("架构师");
            assertThat(result.qualificationLevel()).isEqualTo("LOW");
        }

        verify(mapper).insertQualification(anyLong(), eq("ARCH"), eq("架构师"), eq("LOW"), eq("0"), eq(3), eq(7L), eq(9L));
        verify(mapper, never()).insertExamSubject(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void translatesDatabaseUniquenessFailuresForQualificationCreatesAndUpdates() {
        doThrow(new DataIntegrityViolationException("duplicate qualification"))
            .when(mapper).insertQualification(anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt(), any(), any());

        try (MockedStatic<LoginHelper> login = login()) {
            assertFailure(
                () -> service.createQualification(REQUEST_ID, qualification("ARCH", "架构", "HIGH", "0", 1)),
                409,
                "QUALIFICATION_CONFLICT"
            );
        }

        when(mapper.lockQualification(1L)).thenReturn(qualificationRow(1L, "HIGH"));
        doThrow(new DataIntegrityViolationException("duplicate qualification"))
            .when(mapper).updateQualification(eq(1L), anyString(), anyString(), anyString(), anyString(), anyInt(), any());

        try (MockedStatic<LoginHelper> login = login()) {
            assertFailure(
                () -> service.updateQualification(
                    "1",
                    "e89b5981-37df-4336-87b1-1300b5c3455a",
                    qualification("ARCH", "架构", "HIGH", "0", 1)
                ),
                409,
                "QUALIFICATION_CONFLICT"
            );
        }
    }

    @Test
    void rejectsAdditionalSyllabusesMissingVersionsAndVersionNameConflicts() {
        when(mapper.lockQualification(1L)).thenReturn(qualificationRow(1L, "HIGH"));
        when(mapper.selectVersions(List.of(1L))).thenReturn(List.of(versionRow(2L, null, CREATED_AT)));
        assertFailure(
            () -> service.createVersion("1", REQUEST_ID, version("第二版", null)),
            409,
            "SYLLABUS_ALREADY_EXISTS"
        );

        when(mapper.lockVersion(1L, 2L)).thenReturn(null);
        assertFailure(
            () -> service.updateVersion("1", "2", "e89b5981-37df-4336-87b1-1300b5c3455a", version("第二版", null)),
            404,
            "SYLLABUS_VERSION_NOT_FOUND"
        );

        when(mapper.lockVersion(1L, 2L)).thenReturn(versionRow(2L, null, CREATED_AT));
        doThrow(new DataIntegrityViolationException("duplicate version"))
            .when(mapper).updateVersion(eq(2L), eq(1L), anyString(), any(), any());
        try (MockedStatic<LoginHelper> login = login()) {
            assertFailure(
                () -> service.updateVersion(
                    "1",
                    "2",
                    "f89b5981-37df-4336-87b1-1300b5c3455a",
                    version("第二版", null)
                ),
                409,
                "SYLLABUS_VERSION_NAME_CONFLICT"
            );
        }
    }

    @Test
    void translatesQualificationAndVersionDeletionConstraintFailures() {
        when(mapper.lockQualification(1L)).thenReturn(qualificationRow(1L, "HIGH"));
        when(mapper.selectSourceObjectKeysByQualification(1L)).thenReturn(List.of());
        when(mapper.selectImageObjectKeysByQualification(1L)).thenReturn(List.of());
        doThrow(new DataIntegrityViolationException("qualification referenced"))
            .when(mapper).deleteQualification(1L);

        assertFailure(
            () -> service.deleteQualification("1", REQUEST_ID),
            409,
            "QUALIFICATION_DELETE_FAILED"
        );

        when(mapper.lockVersion(1L, 2L)).thenReturn(versionRow(2L, null, CREATED_AT));
        when(mapper.selectVersionDeleteBlockers(2L)).thenReturn(List.of());
        when(mapper.selectSourceObjectKeysByVersion(2L)).thenReturn(List.of());
        when(mapper.selectImageObjectKeysByVersion(2L)).thenReturn(List.of());
        doThrow(new DataIntegrityViolationException("version referenced"))
            .when(mapper).deleteVersion(2L, 1L);

        assertFailure(
            () -> service.deleteVersion("1", "2", "e89b5981-37df-4336-87b1-1300b5c3455a"),
            409,
            "SYLLABUS_VERSION_DELETE_FAILED"
        );
    }

    @Test
    void replaysVersionAndDeleteResultsWithoutRepeatingDatabaseMutations() throws Exception {
        LocalDate publishedDate = LocalDate.parse("2026-01-01");
        SyllabusVersionWriteBo command = version("  第二版  ", publishedDate);
        String updatePayload = hash(Map.of(
            "certificationId", 1L,
            "id", 2L,
            "name", "第二版",
            "publishedDate", "2026-01-01"
        ));
        SyllabusVersionVo expected = new SyllabusVersionVo(
            "2", "1", "第二版", publishedDate, 4L, CREATED_AT, CREATED_AT
        );
        String updateResponse = jsonMapper.writeValueAsString(Map.of(
            "schema_version", "qualification_version_response/1.0",
            "data", expected
        ));
        when(mapper.selectIdempotency("syllabus_version_update", REQUEST_ID)).thenReturn(
            new CmIdempotencyRecord(updatePayload, "succeeded", 2L, 200, updateResponse)
        );

        SyllabusVersionVo replay = service.updateVersion("1", "2", REQUEST_ID, command);

        assertThat(replay.id()).isEqualTo("2");
        assertThat(replay.versionName()).isEqualTo("第二版");
        assertThat(replay.referenceCount()).isEqualTo(4L);

        String deleteRequestId = "e89b5981-37df-4336-87b1-1300b5c3455a";
        String deletePayload = hash(Map.of("certificationId", 1L, "id", 2L));
        when(mapper.selectIdempotency("syllabus_version_delete", deleteRequestId)).thenReturn(
            new CmIdempotencyRecord(deletePayload, "succeeded", 2L, 200, "{}")
        );

        service.deleteVersion("1", "2", deleteRequestId);

        verify(mapper, never()).lockVersion(anyLong(), anyLong());
        verify(mapper, never()).updateVersion(anyLong(), anyLong(), anyString(), any(), any());
        verify(mapper, never()).deleteVersion(anyLong(), anyLong());

        String corruptRequestId = "f89b5981-37df-4336-87b1-1300b5c3455a";
        when(mapper.selectIdempotency("syllabus_version_update", corruptRequestId)).thenReturn(
            new CmIdempotencyRecord(updatePayload, "succeeded", 2L, 200, "not-json")
        );
        assertFailure(
            () -> service.updateVersion("1", "2", corruptRequestId, command),
            500,
            "QUALIFICATION_VERSION_FAILURE"
        );
    }

    @Test
    void replaysQualificationUpdatesVersionCreatesAndQualificationDeletesWithoutLockingRows() throws Exception {
        QualificationWriteBo qualificationCommand = qualification(" arch ", "  创建时名称  ", "HIGH", "0", 1);
        String qualificationPayload = hash(Map.of(
            "id", 1L,
            "code", "ARCH",
            "name", "创建时名称",
            "level", "HIGH",
            "status", "0",
            "sortOrder", 1
        ));
        var qualification = new org.dromara.certmuse.catalog.domain.vo.QualificationVo(
            "1", "ARCH", "创建时名称", "HIGH", "0", 1, 0, 0, CREATED_AT, CREATED_AT, List.of()
        );
        String qualificationResponse = jsonMapper.writeValueAsString(Map.of(
            "schema_version", "qualification_version_response/1.0",
            "data", qualification
        ));
        when(mapper.selectIdempotency("qualification_update", REQUEST_ID)).thenReturn(
            new CmIdempotencyRecord(qualificationPayload, "succeeded", 1L, 200, qualificationResponse)
        );

        var replayedQualification = service.updateQualification("1", REQUEST_ID, qualificationCommand);

        assertThat(replayedQualification.certificationName()).isEqualTo("创建时名称");

        String versionRequestId = "e89b5981-37df-4336-87b1-1300b5c3455a";
        SyllabusVersionWriteBo versionCommand = version("  第二版  ", null);
        String versionPayload = hash(Map.of(
            "certificationId", 1L,
            "name", "第二版",
            "publishedDate", "null"
        ));
        SyllabusVersionVo version = new SyllabusVersionVo(
            "2", "1", "第二版", null, 0, CREATED_AT, CREATED_AT
        );
        String versionResponse = jsonMapper.writeValueAsString(Map.of(
            "schema_version", "qualification_version_response/1.0",
            "data", version
        ));
        when(mapper.selectIdempotency("syllabus_version_create", versionRequestId)).thenReturn(
            new CmIdempotencyRecord(versionPayload, "succeeded", 2L, 200, versionResponse)
        );

        SyllabusVersionVo replayedVersion = service.createVersion("1", versionRequestId, versionCommand);

        assertThat(replayedVersion.id()).isEqualTo("2");
        assertThat(replayedVersion.versionName()).isEqualTo("第二版");

        String deleteRequestId = "f89b5981-37df-4336-87b1-1300b5c3455a";
        when(mapper.selectIdempotency("qualification_delete", deleteRequestId)).thenReturn(
            new CmIdempotencyRecord(hash(Map.of("id", 1L)), "succeeded", 1L, 200, "{}")
        );

        service.deleteQualification("1", deleteRequestId);

        verify(mapper, never()).lockQualification(anyLong());
        verify(mapper, never()).updateQualification(anyLong(), anyString(), anyString(), anyString(), anyString(), anyInt(), any());
        verify(mapper, never()).deleteQualification(anyLong());
    }

    @Test
    void rejectsIdempotencyRecordsWithAnotherPayloadAnUnfinishedStatusOrNoResource() throws Exception {
        String payload = hash(Map.of("id", 1L));
        List<String> requestIds = List.of(
            REQUEST_ID,
            "e89b5981-37df-4336-87b1-1300b5c3455a",
            "f89b5981-37df-4336-87b1-1300b5c3455a"
        );
        when(mapper.selectIdempotency("qualification_delete", requestIds.get(0))).thenReturn(
            new CmIdempotencyRecord("another-payload", "succeeded", 1L, 200, "{}")
        );
        when(mapper.selectIdempotency("qualification_delete", requestIds.get(1))).thenReturn(
            new CmIdempotencyRecord(payload, "processing", 1L, null, null)
        );
        when(mapper.selectIdempotency("qualification_delete", requestIds.get(2))).thenReturn(
            new CmIdempotencyRecord(payload, "succeeded", null, 200, "{}")
        );

        for (String requestId : requestIds) {
            assertFailure(
                () -> service.deleteQualification("1", requestId),
                409,
                "IDEMPOTENCY_KEY_CONFLICT"
            );
        }

        verify(mapper, never()).lockQualification(anyLong());
    }

    @Test
    void cleansOnlyDistinctObjectKeysAndDefersCleanupUntilTransactionCommit() {
        when(mapper.lockQualification(1L)).thenReturn(qualificationRow(1L, "HIGH"));
        when(mapper.selectSourceObjectKeysByQualification(1L)).thenReturn(null);
        when(mapper.selectImageObjectKeysByQualification(1L)).thenReturn(null);

        service.deleteQualification("1", REQUEST_ID);

        verifyNoInteractions(importPersistence);

        when(mapper.lockVersion(1L, 2L)).thenReturn(versionRow(2L, null, CREATED_AT));
        when(mapper.selectVersionDeleteBlockers(2L)).thenReturn(List.of());
        when(mapper.selectSourceObjectKeysByVersion(2L)).thenReturn(
            Arrays.asList(null, "", "   ", "imports/source.jsonl", "imports/source.jsonl")
        );
        when(mapper.selectImageObjectKeysByVersion(2L)).thenReturn(
            Arrays.asList(null, "question-images/image.png", "question-images/image.png")
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteVersion("1", "2", "e89b5981-37df-4336-87b1-1300b5c3455a");

            verifyNoInteractions(importPersistence);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(importPersistence, times(1)).enqueueCleanup(
                "imports/source.jsonl", "unknown", "qualification_version_delete"
            );
            verify(importPersistence, times(1)).enqueueImageCleanup(
                "question-images/image.png", "unknown", "qualification_version_delete"
            );
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void assertInvalidList(Consumer<QualificationQueryBo> mutation) {
        QualificationQueryBo query = new QualificationQueryBo();
        mutation.accept(query);
        assertFailure(() -> service.list(query), 400, "QUALIFICATION_VERSION_INVALID");
    }

    private void assertInvalidQualification(Consumer<QualificationWriteBo> mutation) {
        QualificationWriteBo command = qualification("ARCH", "架构", "HIGH", "0", 1);
        mutation.accept(command);
        assertFailure(
            () -> service.createQualification(REQUEST_ID, command),
            400,
            "QUALIFICATION_VERSION_INVALID"
        );
    }

    private void assertInvalidVersion(Consumer<SyllabusVersionWriteBo> mutation) {
        SyllabusVersionWriteBo command = version("第二版", null);
        mutation.accept(command);
        assertFailure(
            () -> service.createVersion("1", REQUEST_ID, command),
            400,
            "QUALIFICATION_VERSION_INVALID"
        );
    }

    private static void assertFailure(ThrowingCallable call, int status, String errorCode) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(QualificationVersionException.class, error -> {
                assertThat(error.status()).isEqualTo(status);
                assertThat(error.data().errorCode()).isEqualTo(errorCode);
            });
    }

    private String hash(Object value) throws Exception {
        return DigestUtil.sha256Hex(jsonMapper.writeValueAsString(value));
    }

    private static MockedStatic<LoginHelper> login() {
        MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(7L);
        login.when(LoginHelper::getDeptId).thenReturn(9L);
        return login;
    }

    private static QualificationRow qualificationRow(long id, String level) {
        return new QualificationRow(id, "ARCH", "架构", level, "0", 1, CREATED_AT, CREATED_AT);
    }

    private static SyllabusVersionRow versionRow(long id, LocalDate publishedDate, OffsetDateTime createTime) {
        return new SyllabusVersionRow(id, 1L, "版本" + id, publishedDate, createTime, createTime);
    }

    private static QualificationWriteBo qualification(
        String code,
        String name,
        String level,
        String status,
        Integer sortOrder
    ) {
        QualificationWriteBo command = new QualificationWriteBo();
        command.setCertificationCode(code);
        command.setCertificationName(name);
        command.setQualificationLevel(level);
        command.setStatus(status);
        command.setSortOrder(sortOrder);
        return command;
    }

    private static SyllabusVersionWriteBo version(String name, LocalDate publishedDate) {
        SyllabusVersionWriteBo command = new SyllabusVersionWriteBo();
        command.setVersionName(name);
        command.setPublishedDate(publishedDate);
        return command;
    }
}
