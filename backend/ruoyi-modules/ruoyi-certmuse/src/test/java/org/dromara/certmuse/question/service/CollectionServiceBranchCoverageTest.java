package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.impl.CollectionServiceImpl;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.support.CollectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class CollectionServiceBranchCoverageTest {

    @Mock
    private CollectionMapper mapper;
    @Mock
    private QuestionReviewSubmissionSupport reviewSubmissionSupport;

    private CollectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CollectionServiceImpl(mapper, JsonMapper.builder().build(), reviewSubmissionSupport);
    }

    @Test
    void listNormalizesFiltersAndAppliesRequestedPage() {
        CollectionQueryBo query = new CollectionQueryBo();
        query.setKeyword("  模拟题  ");
        query.setCollectionType(" PRACTICE ");
        query.setStatus(" published ");
        query.setCertificationId("1");
        query.setSyllabusVersionId("2");
        query.setPageNum(3);
        query.setPageSize(10);
        when(mapper.selectCollections(query, 1L, 2L, 10, 20L)).thenReturn(List.of());
        when(mapper.countCollections(query, 1L, 2L)).thenReturn(0L);

        var result = service.list(query);

        assertThat(result.getTotal()).isZero();
        assertThat(query.getKeyword()).isEqualTo("模拟题");
        assertThat(query.getCollectionType()).isEqualTo("PRACTICE");
        assertThat(query.getStatus()).isEqualTo("published");
    }

    @Test
    void listRejectsInvalidFiltersIdentifiersAndPagination() {
        CollectionQueryBo unsupportedType = new CollectionQueryBo();
        unsupportedType.setCollectionType("QUIZ");
        assertInvalid(() -> service.list(unsupportedType), "collectionType非法");

        CollectionQueryBo unsupportedStatus = new CollectionQueryBo();
        unsupportedStatus.setStatus("offline");
        assertInvalid(() -> service.list(unsupportedStatus), "status非法");

        CollectionQueryBo invalidId = new CollectionQueryBo();
        invalidId.setCertificationId("0");
        assertInvalid(() -> service.list(invalidId), "certificationId必须是十进制正整数");

        CollectionQueryBo invalidPage = new CollectionQueryBo();
        invalidPage.setPageSize(101);
        assertInvalid(() -> service.list(invalidPage), "分页参数超出允许范围");
    }

    @Test
    void firstDiagnosticReadinessRequiresAPublishedValidatedDiagnosticRevision() {
        CollectionRevisionRow notPublished = firstDiagnosticRevision("draft");
        CollectionRevisionRow wrongType = revision(11L, 10L, "published");
        CollectionRevisionRow incomplete = firstDiagnosticRevision("published");
        CollectionRevisionRow malformed = firstDiagnosticRevision("published");
        malformed.setCollectionName(null);
        CollectionRevisionRow blankFacts = firstDiagnosticRevision("published");
        blankFacts.setCollectionName(" ");
        CollectionRevisionRow ready = firstDiagnosticRevision("published");
        List<CollectionItemRow> readyItems = readyFirstDiagnosticItems();

        when(mapper.selectRevision(11L)).thenReturn(null, notPublished, wrongType, incomplete, malformed, blankFacts, ready);
        when(mapper.selectItems(11L)).thenReturn(
            List.of(), List.of(malformedFirstDiagnosticItem()), List.of(blankFirstDiagnosticItem()), readyItems
        );
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectActiveSubjectIds(1L)).thenReturn(
            List.of(1L, 2L), List.of(1L, 2L, 3L), List.of(1L, 2L, 3L), List.of(1L, 2L, 3L)
        );

        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
        assertThat(service.isFirstDiagnosticReady(11L)).isTrue();
    }

    @Test
    void firstDiagnosticReadinessRequiresAtLeastTenQuestions() {
        CollectionRevisionRow revision = firstDiagnosticRevision("published");
        when(mapper.selectRevision(11L)).thenReturn(revision);
        when(mapper.selectItems(11L)).thenReturn(readyFirstDiagnosticItems(9));
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectActiveSubjectIds(1L)).thenReturn(List.of(1L, 2L, 3L));

        assertThat(service.isFirstDiagnosticReady(11L)).isFalse();
    }

    @Test
    void manageListDeduplicatesStatusesAndReturnsEmptyPageWithoutRevisionQuery() {
        CollectionQueryBo query = new CollectionQueryBo();
        query.setStatuses(List.of(" draft ", "draft", "", "published"));
        when(mapper.selectManagedCollectionIds(query, null, null, 20, 0L)).thenReturn(List.of());

        var result = service.manageList(query);

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(query.getStatuses()).containsExactly("draft", "published");
        verify(mapper, never()).selectManagedCollectionRevisions(any());
        verify(mapper, never()).countManagedCollections(any(), any(), any());
    }

    @Test
    void detailAndRevisionDetailReturnNotFoundContracts() {
        assertNotFound(() -> service.detail("10"), "COLLECTION_NOT_FOUND");
        assertNotFound(() -> service.revisionDetail("11"), "COLLECTION_REVISION_NOT_FOUND");
    }

    @Test
    void createRejectsInvalidBasicFieldsBeforeStartingIdempotency() {
        CollectionSaveBo command = validCommand(false);
        command.setCollectionName(" ");

        assertInvalid(() -> service.create(requestId(), command), "题集名称不能为空且不能超过200字符");
        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void createRejectsInvalidScopeAndQuestionMetadata() {
        CollectionSaveBo invalidCertification = validCommand(false);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), anyString(), anyString(), eq(null), any()))
            .thenReturn(1);
        when(mapper.countCertification(1L)).thenReturn(0);
        assertInvalid(() -> service.create(requestId(), invalidCertification), "certificationId不存在");

        CollectionSaveBo invalidSyllabus = validCommand(false);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(0);
        assertInvalid(() -> service.create(requestId(), invalidSyllabus), "考纲不属于所选资格");

        CollectionSaveBo duplicate = validCommand(false);
        CollectionSaveBo.ItemBo duplicateItem = item(2, "21", BigDecimal.ONE);
        duplicate.setItems(List.of(duplicate.getItems().getFirst(), duplicateItem));
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        assertInvalid(() -> service.create(requestId(), duplicate), "同一题目修订不能重复加入题集");
    }

    @Test
    void createRejectsAnUnusableQuestionRevisionAndCollectionInsertConflict() {
        CollectionSaveBo unusable = validCommand(false);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), anyString(), anyString(), eq(null), any()))
            .thenReturn(1);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        CollectionItemRow metadata = metadata("archived", "answer/1.0", 1L, 2L);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any())).thenReturn(List.of(metadata));
        assertInvalid(() -> service.create(requestId(), unusable), "题目修订状态无效");

        CollectionSaveBo insertConflict = validCommand(false);
        metadata.setStatus("published");
        when(mapper.insertCollection(anyLong(), eq(1L), eq(2L), anyString(), eq("题集"), eq("PRACTICE"), any(), any(), any(), any()))
            .thenReturn(0);

        assertThatThrownBy(() -> service.create(requestId(), insertConflict))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(409);
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_CREATE_CONFLICT");
            });
    }

    @Test
    void saveReportsMissingRevisionAndConcurrentStateChange() {
        CollectionSaveBo command = validCommand(true);
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        assertNotFound(() -> service.save("11", requestId(), command), "COLLECTION_REVISION_NOT_FOUND");

        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        CollectionRevisionRow submitted = revision(11L, 10L, "pending_review");
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any()))
            .thenReturn(List.of(metadata("published", "answer/1.0", 1L, 2L)));
        when(mapper.updateDraft(eq(11L), eq(0L), anyString(), anyString(), eq(1L), eq(2L), eq(60), eq(1), eq(BigDecimal.TWO)))
            .thenReturn(0);
        when(mapper.selectRevision(11L)).thenReturn(submitted);

        assertConflict(() -> service.save("11", requestId(), command), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void deleteDraftKeepsStableCollectionWhenOtherRevisionsRemain() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        when(mapper.insertIdempotency(anyLong(), eq("delete_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countRevisions(10L)).thenReturn(2);
        when(mapper.deleteDraftRevision(11L)).thenReturn(1);

        var result = service.deleteDraft("11", requestId());

        assertThat(result.status()).isEqualTo("draft");
        verify(mapper, never()).deleteCollection(10L);
    }

    @Test
    void deleteDraftReportsConcurrentTransition() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        when(mapper.insertIdempotency(anyLong(), eq("delete_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countRevisions(10L)).thenReturn(2);
        when(mapper.deleteDraftRevision(11L)).thenReturn(0);
        assertConflict(() -> service.deleteDraft("11", requestId()), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void createRevisionRequiresAnExistingSourceAndStableCollection() {
        when(mapper.lockCollection(10L)).thenReturn(null, 10L);
        assertNotFound(() -> service.createRevision("10", requestId(), null), "COLLECTION_NOT_FOUND");

        when(mapper.selectCurrentPublished(10L)).thenReturn(null);
        assertInvalid(() -> service.createRevision("10", requestId(), null),
            "sourceRevisionId不能为空且当前题集不存在已发布修订");

        CollectionRevisionCreateBo explicit = new CollectionRevisionCreateBo();
        explicit.setSourceRevisionId("11");
        assertNotFound(() -> service.createRevision("10", requestId(), explicit), "COLLECTION_REVISION_NOT_FOUND");
    }

    @Test
    void idempotencyRejectsChangedPayloadInProgressAndCorruptResponses() {
        String changedRequest = requestId();
        CollectionIdempotencyRow changed = idempotency("different", "succeeded", 10L, successEnvelope());
        when(mapper.selectIdempotency("create_collection", changedRequest)).thenReturn(changed);
        assertInvalid(() -> service.create(changedRequest, validCommand(false)), "X-Request-Id已被其他请求使用");

        String runningRequest = requestId();
        CollectionSaveBo runningCommand = validCommand(false);
        normalizeCommand(runningCommand);
        CollectionIdempotencyRow running = idempotency(DigestUtil.sha256Hex(write(runningCommand)), "processing", null, null);
        when(mapper.selectIdempotency("create_collection", runningRequest)).thenReturn(running);
        assertConflict(() -> service.create(runningRequest, runningCommand), "COLLECTION_OPERATION_IN_PROGRESS");

        String corruptRequest = requestId();
        CollectionSaveBo corruptCommand = validCommand(false);
        normalizeCommand(corruptCommand);
        CollectionIdempotencyRow corrupt = idempotency(DigestUtil.sha256Hex(write(corruptCommand)), "succeeded", 10L, "not-json");
        when(mapper.selectIdempotency("create_collection", corruptRequest)).thenReturn(corrupt);
        assertThatThrownBy(() -> service.create(corruptRequest, corruptCommand))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(500);
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_OPERATION_FAILURE");
            });
    }

    @Test
    void failedIdempotencyInsertDistinguishesCompletedReplayFromInProgressRequest() {
        String completedRequest = requestId();
        CollectionSaveBo completedCommand = validCommand(false);
        normalizeCommand(completedCommand);
        String completedHash = DigestUtil.sha256Hex(write(completedCommand));
        CollectionIdempotencyRow completed = idempotency(completedHash, "succeeded", 10L, successEnvelope());
        when(mapper.selectIdempotency("create_collection", completedRequest)).thenReturn(null, completed);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), eq(completedRequest), eq(completedHash), eq(null), any()))
            .thenReturn(0);
        assertConflict(() -> service.create(completedRequest, completedCommand), "COLLECTION_IDEMPOTENCY_REPLAY");

        String runningRequest = requestId();
        CollectionSaveBo runningCommand = validCommand(false);
        normalizeCommand(runningCommand);
        String runningHash = DigestUtil.sha256Hex(write(runningCommand));
        when(mapper.selectIdempotency("create_collection", runningRequest)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), eq(runningRequest), eq(runningHash), eq(null), any()))
            .thenReturn(0);
        assertConflict(() -> service.create(runningRequest, runningCommand), "COLLECTION_OPERATION_IN_PROGRESS");
    }

    @Test
    void submitReviewReportsMissingStableCollection() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(null);

        assertNotFound(() -> service.submitReview("11", requestId()), "COLLECTION_NOT_FOUND");
    }

    @Test
    void submitReviewReportsConcurrentTransitionConflict() {
        String conflictRequest = requestId();
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        CollectionItemRow item = validItem();
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(reviewSubmissionSupport.lockVisibleRevisions(eq(List.of(21L)), any()))
            .thenReturn(List.of(question(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(conflictRequest), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.markSubmitted(11L, null)).thenReturn(0);

        assertConflict(() -> service.submitReview("11", conflictRequest), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void approveReportsSupersededRevisionTransitionConflict() {
        String requestId = requestId();
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review");
        CollectionRevisionRow previous = revision(12L, 10L, "published");
        CollectionItemRow item = validItem();
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), null))
            .thenReturn(List.of(question(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("approve_collection"), eq(requestId), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockPublishedRevisions(10L)).thenReturn(List.of(previous));
        when(mapper.markPublishedAsDraft(12L)).thenReturn(0);
        when(mapper.selectRevision(12L)).thenReturn(previous);

        assertConflict(() -> service.approve("11", requestId), "COLLECTION_REVISION_STATE_CONFLICT");
        verify(mapper, never()).markPublished(eq(11L), any());
    }

    @Test
    void offlineAndRejectReportStateTransitionConflicts() {
        String offlineRequest = requestId();
        CollectionRevisionRow published = revision(11L, 10L, "published");
        when(mapper.insertIdempotency(anyLong(), eq("offline_collection_revision"), eq(offlineRequest), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(published);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(published);
        when(mapper.lockCurrentPublished(10L)).thenReturn(published);
        when(mapper.deleteCurrent(10L, 11L)).thenReturn(0);
        assertConflict(() -> service.offline("11", offlineRequest), "COLLECTION_OFFLINE_INVALID_STATE");

        String rejectRequest = requestId();
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review");
        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), eq(rejectRequest), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.markRejected(11L, null, "依据不足")).thenReturn(0);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        assertConflict(() -> service.reject("11", rejectRequest, " 依据不足 "), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void listRejectsBothNonPositivePaginationInputs() {
        CollectionQueryBo zeroPage = new CollectionQueryBo();
        zeroPage.setPageNum(0);
        assertInvalid(() -> service.list(zeroPage), "分页参数超出允许范围");

        CollectionQueryBo zeroSize = new CollectionQueryBo();
        zeroSize.setPageSize(0);
        assertInvalid(() -> service.list(zeroSize), "分页参数超出允许范围");
    }

    @Test
    void detailAndRevisionDetailExposeTheWorkingRevisionStateThroughPublicReads() {
        CollectionRevisionRow published = revision(11L, 10L, "published");
        CollectionRevisionRow pending = revision(12L, 10L, "pending_review");
        when(mapper.selectDisplayRevision(10L)).thenReturn(published);
        when(mapper.selectCurrentPublished(10L)).thenReturn(published);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(pending);
        when(mapper.selectRevisions(10L)).thenReturn(List.of(published, pending));
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.selectRevision(12L)).thenReturn(pending);
        when(mapper.selectItems(12L)).thenReturn(List.of());

        var detail = service.detail("10");
        var revision = service.revisionDetail("12");

        assertThat(detail.currentPublishedRevision().currentPublished()).isTrue();
        assertThat(detail.allowedActions()).containsExactly("view");
        assertThat(revision.allowedActions()).containsExactly("view");
    }

    @Test
    void deleteDraftDeletesTheAggregateWhenItContainsOnlyThatRevision() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        when(mapper.insertIdempotency(anyLong(), eq("delete_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countRevisions(10L)).thenReturn(1);
        when(mapper.deleteDraftRevision(11L)).thenReturn(1);

        service.deleteDraft("11", requestId());

        verify(mapper).deleteCollection(10L);
    }

    @Test
    void saveReportsWhenTheRevisionWasDeletedDuringAConcurrentUpdate() {
        CollectionSaveBo command = validCommand(true);
        CollectionRevisionRow draft = revision(11L, 10L, "draft");
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any()))
            .thenReturn(List.of(metadata("published", "answer/1.0", 1L, 2L)));
        when(mapper.updateDraft(eq(11L), eq(0L), anyString(), anyString(), eq(1L), eq(2L), eq(60), eq(1), eq(BigDecimal.TWO)))
            .thenReturn(0);

        assertNotFound(() -> service.save("11", requestId(), command), "COLLECTION_REVISION_NOT_FOUND");
    }

    @Test
    void createRevisionRejectsARevisionFromAnotherCollection() {
        CollectionRevisionCreateBo command = new CollectionRevisionCreateBo();
        command.setSourceRevisionId("11");
        CollectionRevisionRow source = revision(11L, 99L, "published");
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectRevision(11L)).thenReturn(source);

        assertConflict(() -> service.createRevision("10", requestId(), command), "COLLECTION_REVISION_SOURCE_INVALID");
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CollectionException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(400);
            assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_REQUEST_INVALID");
            assertThat(exception.getMessage()).isEqualTo(message);
        });
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CollectionException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(404);
            assertThat(exception.getData().errorCode()).isEqualTo(code);
        });
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CollectionException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(409);
            assertThat(exception.getData().errorCode()).isEqualTo(code);
        });
    }

    private static CollectionSaveBo validCommand(boolean includeVersion) {
        CollectionSaveBo command = new CollectionSaveBo();
        command.setCollectionName(" 题集 ");
        command.setCollectionType(" PRACTICE ");
        command.setCertificationId("1");
        command.setSyllabusVersionId("2");
        command.setDurationMinutes(60);
        command.setRowVersion(includeVersion ? "0" : null);
        command.setItems(List.of(item(1, "21", BigDecimal.TWO)));
        return command;
    }

    private static CollectionSaveBo.ItemBo item(int order, String revisionId, BigDecimal score) {
        CollectionSaveBo.ItemBo item = new CollectionSaveBo.ItemBo();
        item.setItemOrder(order);
        item.setQuestionRevisionId(revisionId);
        item.setReportScore(score);
        return item;
    }

    private static CollectionItemRow metadata(String status, String schema, Long certificationId, Long syllabusId) {
        CollectionItemRow item = new CollectionItemRow();
        item.setQuestionRevisionId(21L);
        item.setStatus(status);
        item.setAnswerSchema(schema);
        item.setCertificationId(certificationId);
        item.setSyllabusVersionId(syllabusId);
        return item;
    }

    private static CollectionItemRow validItem() {
        CollectionItemRow item = metadata("published", "answer/1.0", 1L, 2L);
        item.setItemOrder(1);
        item.setReportScore(BigDecimal.TWO);
        item.setQuestionType("CHOICE");
        item.setExamSubjectId(3L);
        return item;
    }

    private static CollectionRevisionRow revision(long id, long collectionId, String status) {
        CollectionRevisionRow row = new CollectionRevisionRow();
        row.setId(id);
        row.setCollectionId(collectionId);
        row.setCollectionCode("COL-1");
        row.setRevisionNo(1);
        row.setStatus(status);
        row.setRowVersion(0L);
        row.setCollectionName("题集");
        row.setCollectionType("PRACTICE");
        row.setCertificationId(1L);
        row.setSyllabusVersionId(2L);
        row.setDurationMinutes(60);
        row.setQuestionCount(1);
        row.setTotalReportScore(BigDecimal.TWO);
        row.setUpdatedTime(OffsetDateTime.parse("2026-08-12T09:00:00+08:00"));
        return row;
    }

    private static CollectionRevisionRow firstDiagnosticRevision(String status) {
        CollectionRevisionRow row = revision(11L, 10L, status);
        row.setCollectionType("FIRST_DIAGNOSTIC");
        row.setQuestionCount(10);
        row.setTotalReportScore(BigDecimal.TEN);
        return row;
    }

    private static List<CollectionItemRow> readyFirstDiagnosticItems() {
        List<CollectionItemRow> items = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            CollectionItemRow item = metadata("published", "answer/1.0", 1L, 2L);
            item.setQuestionRevisionId((long) (100 + index));
            item.setItemOrder(index);
            item.setReportScore(BigDecimal.ONE);
            item.setQuestionType("CHOICE");
            item.setExamSubjectId((long) ((index - 1) % 3 + 1));
            item.setEstimatedSeconds(60);
            item.setInvalidKnowledgeCount(0);
            item.setMissingLeafImportanceCount(0);
            items.add(item);
        }
        return items;
    }

    private static List<CollectionItemRow> readyFirstDiagnosticItems(int count) {
        return readyFirstDiagnosticItems().subList(0, count);
    }

    private static CollectionItemRow malformedFirstDiagnosticItem() {
        CollectionItemRow item = metadata("published", null, 1L, 2L);
        item.setItemOrder(1);
        item.setReportScore(BigDecimal.ONE);
        item.setQuestionType("ESSAY");
        item.setExamSubjectId(1L);
        item.setEstimatedSeconds(null);
        item.setInvalidKnowledgeCount(null);
        item.setMissingLeafImportanceCount(null);
        item.setKnowledgePointId(99L);
        return item;
    }

    private static CollectionItemRow blankFirstDiagnosticItem() {
        CollectionItemRow item = metadata("published", " ", 1L, 2L);
        item.setItemOrder(1);
        item.setReportScore(BigDecimal.ONE);
        item.setQuestionType("CHOICE");
        item.setExamSubjectId(1L);
        item.setEstimatedSeconds(0);
        item.setInvalidKnowledgeCount(1);
        item.setMissingLeafImportanceCount(1);
        item.setKnowledgePointId(99L);
        return item;
    }

    private static org.dromara.certmuse.question.domain.QuestionRows.Revision question(long id, String status) {
        var row = new org.dromara.certmuse.question.domain.QuestionRows.Revision();
        row.setRevisionId(id);
        row.setStatus(status);
        return row;
    }

    private static CollectionIdempotencyRow idempotency(
        String payloadHash, String status, Long resourceId, String responseBody
    ) {
        CollectionIdempotencyRow row = new CollectionIdempotencyRow();
        row.setPayloadHash(payloadHash);
        row.setStatus(status);
        row.setResourceId(resourceId);
        row.setResponseBody(responseBody);
        return row;
    }

    private static String successEnvelope() {
        return """
            {"schema_version":"1.0","response":{"code":200,"msg":"ok","data":{
              "collectionId":"10","collectionCode":"COL-1","revisionId":"11","revisionNo":1,
              "status":"draft","rowVersion":"0","createdRevision":false,"sourceRevisionId":null}}}
            """;
    }

    private static String write(CollectionSaveBo command) {
        return JsonMapper.builder().build().writeValueAsString(command);
    }

    private static void normalizeCommand(CollectionSaveBo command) {
        command.setCollectionName(command.getCollectionName().trim());
        command.setCollectionType(command.getCollectionType().trim());
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
