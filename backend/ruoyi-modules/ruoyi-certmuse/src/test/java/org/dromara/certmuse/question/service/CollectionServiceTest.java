package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionListRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRenameBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.domain.vo.CollectionPublishCheckVo;
import org.dromara.certmuse.question.domain.vo.CollectionMutationVo;
import org.dromara.certmuse.question.mapper.CollectionMapper;
import org.dromara.certmuse.question.service.impl.CollectionServiceImpl;
import org.dromara.certmuse.question.service.impl.QuestionReviewSubmissionSupport;
import org.dromara.certmuse.question.support.CollectionException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class CollectionServiceTest {
    private final CollectionMapper mapper = mock(CollectionMapper.class);
    private final QuestionReviewSubmissionSupport reviewSubmissionSupport = mock(QuestionReviewSubmissionSupport.class);
    private final CollectionServiceImpl service = new CollectionServiceImpl(mapper, JsonMapper.builder().build(),
        reviewSubmissionSupport);

    @Test
    void blocksCreatingWhenAnotherRevisionIsPendingReview() {
        CollectionRevisionRow working = revision(12L, 10L, 2, "pending_review", "PRACTICE");
        CollectionRevisionCreateBo command = new CollectionRevisionCreateBo();
        command.setSourceRevisionId("11");
        when(mapper.insertIdempotency(anyLong(), eq("create_collection_revision"), any(), any(), eq(10L), any())).thenReturn(1);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(working);

        assertThatThrownBy(() -> service.createRevision("10", UUID.randomUUID().toString(), command))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_PENDING_REVIEW_EXISTS");
                assertThat(exception.getData().workingRevisionId()).isEqualTo("12");
            });
    }

    @Test
    void managementListGroupsAllRevisionsUnderOneCollectionParent() {
        CollectionListRow rejected = listRow("10", "11", 2, "rejected", true, false);
        CollectionListRow published = listRow("10", "12", 1, "published", false, true);
        when(mapper.selectManagedCollectionIds(any(), any(), any(), eq(20), eq(0L))).thenReturn(List.of(10L));
        when(mapper.selectManagedCollectionRevisions(List.of(10L))).thenReturn(List.of(rejected, published));
        when(mapper.countManagedCollections(any(), any(), any())).thenReturn(1L);

        var result = service.manageList(new CollectionQueryBo());
        var parent = result.getRows().iterator().next();

        assertThat(result.getRows()).hasSize(1);
        assertThat(parent.revisions()).hasSize(2);
        assertThat(parent.revisions().getFirst().status()).isEqualTo("rejected");
        assertThat(parent.revisions().getFirst().hasReviewOpinion()).isTrue();
        assertThat(parent.currentPublishedRevisionId()).isEqualTo("12");
        assertThat(parent.collectionName()).isEqualTo("父题集");
        assertThat(parent.updatedTime()).isEqualTo(OffsetDateTime.parse("2026-08-07T00:00:00+08:00"));
    }

    @Test
    void listsCollectionsAndMapsTheManagementProjection() {
        when(mapper.selectCollections(any(), eq(null), eq(null), eq(20), eq(0L)))
            .thenReturn(List.of(listRow("10", "11", 1, "draft", false, false)));
        when(mapper.countCollections(any(), eq(null), eq(null))).thenReturn(1L);

        var result = service.list(new CollectionQueryBo());

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.collectionId()).isEqualTo("10");
            assertThat(row.revisionId()).isEqualTo("11");
            assertThat(row.status()).isEqualTo("draft");
        });
    }

    @Test
    void detailsExposePublishedHistoryAndCreateRevisionAction() {
        CollectionRevisionRow published = revision(11L, 10L, 1, "published", "PRACTICE");
        when(mapper.selectDisplayRevision(10L)).thenReturn(published);
        when(mapper.selectCurrentPublished(10L)).thenReturn(published);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(null);
        when(mapper.selectRevisions(10L)).thenReturn(List.of(published));
        when(mapper.selectItems(11L)).thenReturn(List.of());

        var result = service.detail("10");

        assertThat(result.collectionId()).isEqualTo("10");
        assertThat(result.currentPublishedRevision().revisionId()).isEqualTo("11");
        assertThat(result.allowedActions()).containsExactly("view", "create_revision");
        assertThat(result.revisions()).singleElement().satisfies(summary ->
            assertThat(summary.currentPublished()).isTrue());
    }

    @Test
    void createsACollectionDraftAfterValidatingQuestionMetadata() {
        CollectionSaveBo command = saveCommand(null);
        when(mapper.selectIdempotency(eq("create_collection"), any())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), any(), any(), eq(null), any())).thenReturn(1);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectQuestionRevisionMetadata(any(), eq(7L))).thenReturn(List.of(metadataItem()));
        when(mapper.insertCollection(anyLong(), eq(1L), eq(2L), any(), eq("题集"), eq("PRACTICE"), any(), any(), any(), any())).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            CollectionMutationVo result = service.create(UUID.randomUUID().toString(), command);

            assertThat(result.status()).isEqualTo("draft");
            assertThat(result.revisionNo()).isEqualTo(1);
            assertThat(result.createdRevision()).isFalse();
        }

        verify(mapper).insertRevision(anyLong(), anyLong(), eq(1), eq("题集"), eq("PRACTICE"),
            eq(1L), eq(2L), eq(60), eq(1), eq(BigDecimal.TWO));
        verify(mapper).insertItem(anyLong(), anyLong(), eq(21L), eq(1), eq(BigDecimal.TWO), eq("1.0"));
        verify(mapper).completeIdempotency(anyLong(), anyLong(), eq(200), any());
    }

    @Test
    void savesAnEditableDraftWithOptimisticVersionAndReplacesItsItems() {
        CollectionSaveBo command = saveCommand("0");
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        when(mapper.selectIdempotency(eq("save_collection_revision"), any())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectQuestionRevisionMetadata(any(), eq(7L))).thenReturn(List.of(metadataItem()));
        when(mapper.updateDraft(eq(11L), eq(0L), eq("题集"), eq("PRACTICE"), eq(1L), eq(2L),
            eq(60), eq(1), eq(BigDecimal.TWO))).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);

            CollectionMutationVo result = service.save("11", UUID.randomUUID().toString(), command);

            assertThat(result.status()).isEqualTo("draft");
            assertThat(result.rowVersion()).isEqualTo("1");
        }

        verify(mapper).deleteItems(11L);
        verify(mapper).insertItem(anyLong(), eq(11L), eq(21L), eq(1), eq(BigDecimal.TWO), eq("1.0"));
        verify(mapper).completeIdempotency(anyLong(), eq(11L), eq(200), any());
    }

    @Test
    void renamesOnlyTheStableCollectionParentAndTrimsTheName() {
        CollectionRenameBo command = new CollectionRenameBo();
        command.setCollectionName("  新父题集名称  ");
        when(mapper.selectIdempotency(eq("rename_collection"), any())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("rename_collection"), any(), any(), eq(10L), any())).thenReturn(1);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockDraftRevisionForRename(10L)).thenReturn(11L);
        when(mapper.updateCollectionName(10L, "新父题集名称")).thenReturn(1);

        var result = service.rename("10", UUID.randomUUID().toString(), command);

        assertThat(result.collectionId()).isEqualTo("10");
        assertThat(result.collectionName()).isEqualTo("新父题集名称");
        verify(mapper).updateCollectionName(10L, "新父题集名称");
        verify(mapper).lockDraftRevisionForRename(10L);
        verify(mapper).completeIdempotency(anyLong(), eq(10L), eq(200), any());
        verify(mapper, never()).updateDraft(anyLong(), anyLong(), any(), any(), anyLong(), anyLong(),
            anyInt(), anyInt(), any());
    }

    @Test
    void replaysACompletedParentRenameWithoutWritingAgain() {
        String requestId = UUID.randomUUID().toString();
        CollectionRenameBo command = new CollectionRenameBo();
        command.setCollectionName("新父题集名称");
        CollectionIdempotencyRow record = new CollectionIdempotencyRow();
        record.setPayloadHash(DigestUtil.sha256Hex("rename-collection:10\n新父题集名称"));
        record.setStatus("succeeded");
        record.setResourceId(10L);
        record.setResponseBody("{\"schema_version\":\"1.0\",\"response\":{\"code\":200,\"msg\":\"题集名称已更新\",\"data\":{\"collectionId\":\"10\",\"collectionName\":\"新父题集名称\"}}}");
        when(mapper.selectIdempotency("rename_collection", requestId)).thenReturn(record);

        var result = service.rename("10", requestId, command);

        assertThat(result.collectionName()).isEqualTo("新父题集名称");
        verify(mapper, never()).lockCollection(anyLong());
        verify(mapper, never()).lockDraftRevisionForRename(anyLong());
        verify(mapper, never()).updateCollectionName(anyLong(), any());
    }

    @Test
    void rejectsParentRenameWhenTheCollectionHasNoDraftRevision() {
        CollectionRenameBo command = new CollectionRenameBo();
        command.setCollectionName("新父题集名称");
        when(mapper.selectIdempotency(eq("rename_collection"), any())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("rename_collection"), any(), any(), eq(10L), any())).thenReturn(1);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockDraftRevisionForRename(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.rename("10", UUID.randomUUID().toString(), command))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(409);
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_RENAME_INVALID_STATE");
            });

        verify(mapper, never()).updateCollectionName(anyLong(), any());
        verify(mapper, never()).completeIdempotency(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void rejectsStaleDraftVersionsAndIllegalOfflineTargets() {
        CollectionSaveBo command = saveCommand("0");
        CollectionRevisionRow stale = revision(11L, 10L, 1, "draft", "PRACTICE");
        stale.setRowVersion(3L);
        when(mapper.selectIdempotency(eq("save_collection_revision"), any())).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(stale);

        assertThatThrownBy(() -> service.save("11", UUID.randomUUID().toString(), command))
            .isInstanceOfSatisfying(CollectionException.class, exception ->
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_REVISION_VERSION_CONFLICT"));
        verify(mapper, never()).updateDraft(anyLong(), anyLong(), any(), any(), anyLong(), anyLong(), anyInt(), anyInt(), any());

        CollectionRevisionRow published = revision(11L, 10L, 1, "published", "PRACTICE");
        CollectionRevisionRow anotherCurrent = revision(12L, 10L, 2, "published", "PRACTICE");
        when(mapper.insertIdempotency(anyLong(), eq("offline_collection_revision"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(published);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(published);
        when(mapper.lockCurrentPublished(10L)).thenReturn(anotherCurrent);

        assertThatThrownBy(() -> service.offline("11", UUID.randomUUID().toString()))
            .isInstanceOfSatisfying(CollectionException.class, exception ->
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_OFFLINE_INVALID_STATE"));
        verify(mapper, never()).deleteCurrent(anyLong(), anyLong());
    }

    @Test
    void copiesAnySelectedHistoryIntoNextRevision() {
        CollectionRevisionRow source = revision(11L, 10L, 1, "draft", "PRACTICE");
        CollectionRevisionCreateBo command = new CollectionRevisionCreateBo();
        command.setSourceRevisionId("11");
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectRevision(11L)).thenReturn(source);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection_revision"), any(), any(), eq(10L), any())).thenReturn(1);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(null);
        when(mapper.nextRevisionNo(10L)).thenReturn(3);
        when(mapper.selectItems(11L)).thenReturn(List.of());

        var result = service.createRevision("10", UUID.randomUUID().toString(), command);

        assertThat(result.revisionNo()).isEqualTo(3);
        assertThat(result.sourceRevisionId()).isEqualTo("11");
        assertThat(result.status()).isEqualTo("draft");
    }

    @Test
    void rejectsHistoryFromAnotherCollection() {
        CollectionRevisionRow source = revision(11L, 99L, 1, "published", "PRACTICE");
        CollectionRevisionCreateBo command = new CollectionRevisionCreateBo();
        command.setSourceRevisionId("11");
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectRevision(11L)).thenReturn(source);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection_revision"), any(), any(), eq(10L), any())).thenReturn(1);

        assertThatThrownBy(() -> service.createRevision("10", UUID.randomUUID().toString(), command))
            .isInstanceOfSatisfying(CollectionException.class, exception ->
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_REVISION_SOURCE_INVALID"));
    }

    @Test
    void blocksSubmittingWhenAnotherRevisionIsPendingReview() {
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        CollectionRevisionRow pending = revision(12L, 10L, 2, "pending_review", "PRACTICE");
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(pending);

        assertThatThrownBy(() -> service.submitReview("11", UUID.randomUUID().toString()))
            .isInstanceOfSatisfying(CollectionException.class, exception ->
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_PENDING_REVIEW_EXISTS"));
    }

    @Test
    void approvingDemotesEveryPublishedRevisionBeforeSwitchingPointer() {
        CollectionRevisionRow pending = revision(11L, 10L, 2, "pending_review", "PRACTICE");
        CollectionRevisionRow previous = revision(10L, 10L, 1, "published", "PRACTICE");
        CollectionItemRow item = validItem();
        when(mapper.insertIdempotency(anyLong(), eq("approve_collection"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), null))
            .thenReturn(List.of(questionRevision(21L, "published")));
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.lockPublishedRevisions(10L)).thenReturn(List.of(previous));
        when(mapper.markPublishedAsDraft(10L)).thenReturn(1);
        when(mapper.markPublished(11L, null)).thenReturn(1);

        var result = service.approve("11", UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo("published");
        org.mockito.Mockito.verify(mapper).markPublishedAsDraft(10L);
        org.mockito.Mockito.verify(mapper).upsertCurrent(10L, 11L);
    }

    @Test
    void createsRevisionFromCurrentPublishedWhenSourceIsOmitted() {
        CollectionRevisionRow source = revision(11L, 10L, 1, "published", "PRACTICE");
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectPendingReviewRevision(10L)).thenReturn(null);
        when(mapper.selectCurrentPublished(10L)).thenReturn(source);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection_revision"), any(), any(), eq(10L), any())).thenReturn(1);
        when(mapper.nextRevisionNo(10L)).thenReturn(2);
        when(mapper.selectItems(11L)).thenReturn(List.of());

        var result = service.createRevision("10", UUID.randomUUID().toString(), new CollectionRevisionCreateBo());

        assertThat(result.sourceRevisionId()).isEqualTo("11");
        assertThat(result.revisionNo()).isEqualTo(2);
    }

    @Test
    void offliningCurrentRevisionClearsPointerAndReturnsDraft() {
        CollectionRevisionRow published = revision(11L, 10L, 1, "published", "PRACTICE");
        when(mapper.insertIdempotency(anyLong(), eq("offline_collection_revision"), any(), any(), eq(11L), any())).thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(published);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(published);
        when(mapper.lockCurrentPublished(10L)).thenReturn(published);
        when(mapper.deleteCurrent(10L, 11L)).thenReturn(1);
        when(mapper.markPublishedAsDraft(11L)).thenReturn(1);

        var result = service.offline("11", UUID.randomUUID().toString());

        assertThat(result.status()).isEqualTo("draft");
        assertThat(result.rowVersion()).isEqualTo("1");
        org.mockito.Mockito.verify(mapper).deleteCurrent(10L, 11L);
    }

    @Test
    void firstDiagnosticRequiresAtLeastTenQuestions() throws Exception {
        CollectionRevisionRow revision = revision(11L, 10L, 1, "draft", "FIRST_DIAGNOSTIC");
        revision.setQuestionCount(1);
        revision.setTotalReportScore(BigDecimal.TWO);
        CollectionItemRow item = validItem();
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectActiveSubjectIds(1L)).thenReturn(List.of(3L, 4L, 5L));

        CollectionPublishCheckVo result = publishCheck(revision);

        assertThat(result.passed()).isFalse();
        assertThat(result.blockingIssues()).extracting(CollectionPublishCheckVo.IssueVo::code)
            .contains("FIRST_DIAGNOSTIC_QUESTION_COUNT", "FIRST_DIAGNOSTIC_SUBJECT_COVERAGE");
    }

    @Test
    void practiceWithValidPublishedQuestionPassesCheck() throws Exception {
        CollectionRevisionRow revision = revision(11L, 10L, 1, "draft", "PRACTICE");
        revision.setQuestionCount(1);
        revision.setTotalReportScore(BigDecimal.TWO);
        when(mapper.selectItems(11L)).thenReturn(List.of(validItem()));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), 7L))
            .thenReturn(List.of(questionRevision(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);

        assertThat(publishCheck(revision).passed()).isTrue();
    }

    @Test
    void deletesTheOnlyDraftAndItsStableCollection() {
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countRevisions(10L)).thenReturn(1);
        when(mapper.deleteDraftRevision(11L)).thenReturn(1);
        when(mapper.deleteCollection(10L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("delete_collection_revision"), any(), any(), eq(11L), any())).thenReturn(1);

        service.deleteDraft("11", UUID.randomUUID().toString());

        org.mockito.Mockito.verify(mapper).deleteItems(11L);
        org.mockito.Mockito.verify(mapper).deleteCollection(10L);
    }

    @Test
    void rejectsReviewOpinionOverFiveHundredCharacters() {
        assertThatThrownBy(() -> service.reject("11", UUID.randomUUID().toString(), "x".repeat(501)))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_REQUEST_INVALID");
                assertThat(exception.getMessage()).isEqualTo("审核意见不能为空且不能超过500字符");
            });
    }

    @Test
    void submitsValidDraftWithIncrementedVersionAndAudit() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        draft.setRowVersion(4L); draft.setQuestionCount(1); draft.setTotalReportScore(BigDecimal.TWO);
        when(mapper.selectIdempotency("submit_collection_review", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(requestId), any(), eq(11L), any())).thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of(validItem()));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), 7L))
            .thenReturn(List.of(questionRevision(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.markSubmitted(11L, 7L)).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            CollectionMutationVo result = service.submitReview("11", requestId);

            assertThat(result.status()).isEqualTo("pending_review");
            assertThat(result.rowVersion()).isEqualTo("5");
        }

        verify(mapper).insertAudit(anyLong(), eq(7L), eq("submit_collection_review"), eq(11L), any(), any(), any());
        verify(mapper).completeIdempotency(anyLong(), eq(11L), eq(200), any());
        verify(reviewSubmissionSupport, never()).submitDraft(any(), any(), any(), any());
    }

    @Test
    void aggregatesQuestionIssuesAndWritesNothingWhenJointGateFails() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        draft.setQuestionCount(2); draft.setTotalReportScore(BigDecimal.valueOf(4));
        CollectionItemRow first = validItem(); first.setStatus("draft");
        CollectionItemRow second = validItem(); second.setItemOrder(2); second.setQuestionRevisionId(22L); second.setStatus("draft");
        QuestionRows.Revision firstQuestion = questionRevision(21L, "draft");
        QuestionRows.Revision secondQuestion = questionRevision(22L, "draft");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of(first, second));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L, 22L), 7L))
            .thenReturn(List.of(firstQuestion, secondQuestion));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(reviewSubmissionSupport.validate(firstQuestion)).thenReturn(List.of(
            new org.dromara.certmuse.question.domain.vo.QuestionErrorVo.BlockingIssueVo(
                "QUESTION_STEM_REQUIRED", "stem", "题干不能为空")));
        when(reviewSubmissionSupport.validate(secondQuestion)).thenReturn(List.of(
            new org.dromara.certmuse.question.domain.vo.QuestionErrorVo.BlockingIssueVo(
                "QUESTION_ANSWER_REQUIRED", "answer", "答案不能为空")));

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> service.submitReview("11", requestId))
                .isInstanceOfSatisfying(CollectionException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(422);
                    assertThat(exception.getData().blockingIssues())
                        .extracting(CollectionPublishCheckVo.IssueVo::itemOrder)
                        .contains(1, 2);
                });
        }

        verify(mapper, never()).insertIdempotency(anyLong(), any(), any(), any(), any(), any());
        verify(reviewSubmissionSupport, never()).submitDraft(any(), any(), any(), any());
        verify(mapper, never()).markSubmitted(anyLong(), any());
    }

    @Test
    void resubmitsRejectedCollectionWithAQualificationScopedQuestion() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow rejectedCollection = revision(11L, 10L, 1, "rejected", "PRACTICE");
        rejectedCollection.setQuestionCount(2); rejectedCollection.setTotalReportScore(BigDecimal.valueOf(4));
        CollectionItemRow pending = validItem(); pending.setStatus("pending_review");
        CollectionItemRow rejected = validItem(); rejected.setItemOrder(2); rejected.setQuestionRevisionId(22L);
        rejected.setStatus("rejected");
        rejected.setKnowledgePointId(null);
        rejected.setSyllabusVersionId(null);
        when(mapper.selectRevision(11L)).thenReturn(rejectedCollection);
        when(mapper.selectItems(11L)).thenReturn(List.of(pending, rejected));
        QuestionRows.Revision pendingQuestion = questionRevision(21L, "pending_review");
        QuestionRows.Revision rejectedQuestion = questionRevision(22L, "rejected");
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L, 22L), 7L)).thenReturn(List.of(
            pendingQuestion, rejectedQuestion));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(rejectedCollection);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(requestId), any(), eq(11L), any())).thenReturn(1);
        when(mapper.markSubmitted(11L, 7L)).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThat(service.submitReview("11", requestId).status()).isEqualTo("pending_review");
        }

        verify(reviewSubmissionSupport).validate(rejectedQuestion);
        verify(reviewSubmissionSupport).submitDraft(eq(rejectedQuestion), eq(7L), eq(requestId), any());
        verify(mapper).markSubmitted(11L, 7L);
    }

    @Test
    void rejectsConcurrentItemListChangeBeforeAnyJointWrite() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        CollectionItemRow before = validItem();
        CollectionItemRow after = validItem(); after.setQuestionRevisionId(22L);
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of(before), List.of(after));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), 7L))
            .thenReturn(List.of(questionRevision(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThatThrownBy(() -> service.submitReview("11", requestId))
                .isInstanceOfSatisfying(CollectionException.class, exception ->
                    assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_ITEMS_CONCURRENTLY_CHANGED"));
        }

        verify(mapper, never()).insertIdempotency(anyLong(), any(), any(), any(), any(), any());
        verify(reviewSubmissionSupport, never()).submitDraft(any(), any(), any(), any());
    }

    @Test
    void replaysJointSubmitWithoutLockingOrWritingAgain() {
        String requestId = UUID.randomUUID().toString();
        CollectionIdempotencyRow record = new CollectionIdempotencyRow();
        record.setPayloadHash(DigestUtil.sha256Hex("submit:11"));
        record.setStatus("succeeded");
        record.setResourceId(11L);
        record.setResponseBody("""
            {"schema_version":"1.0","response":{"code":200,"msg":"题集已提交审核","data":{
              "collectionId":"10","collectionCode":"COL-1","revisionId":"11","revisionNo":1,
              "status":"pending_review","rowVersion":"2","createdRevision":false,"sourceRevisionId":null}}}
            """);
        when(mapper.selectIdempotency("submit_collection_review", requestId)).thenReturn(record);

        CollectionMutationVo result = service.submitReview("11", requestId);

        assertThat(result.status()).isEqualTo("pending_review");
        assertThat(result.rowVersion()).isEqualTo("2");
        verify(mapper, never()).selectRevision(anyLong());
        verify(reviewSubmissionSupport, never()).lockVisibleRevisions(any(), any());
        verify(mapper, never()).markSubmitted(anyLong(), any());
    }

    @Test
    void refusesApprovalWhenReferencedQuestionCannotBeLocked() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow pending = revision(11L, 10L, 1, "pending_review", "PRACTICE");
        pending.setQuestionCount(1); pending.setTotalReportScore(BigDecimal.TWO);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of(validItem()));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), null)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);

        assertThatThrownBy(() -> service.approve("11", requestId))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(422);
                assertThat(exception.getData().blockingIssues()).extracting(CollectionPublishCheckVo.IssueVo::code)
                    .contains("QUESTION_REVISION_NOT_VISIBLE");
            });

        verify(mapper, never()).insertIdempotency(anyLong(), any(), any(), any(), any(), any());
        verify(mapper, never()).markPublished(anyLong(), any());
        verify(mapper, never()).upsertCurrent(anyLong(), anyLong());
    }

    @Test
    void keepsDraftWhenSubmitReviewCheckFailsAndReturnsAllIssues() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        when(mapper.selectIdempotency("submit_collection_review", requestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(requestId), any(), eq(11L), any())).thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);

        assertThatThrownBy(() -> service.submitReview("11", requestId))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(422);
                assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_PUBLISH_CHECK_FAILED");
                assertThat(exception.getData().blockingIssues()).extracting(CollectionPublishCheckVo.IssueVo::code)
                    .contains("COLLECTION_QUESTION_REQUIRED");
            });

        verify(mapper, never()).markSubmitted(anyLong(), any());
        verify(mapper, never()).insertAudit(anyLong(), any(), any(), anyLong(), any(), any(), any());
        verify(mapper, never()).completeIdempotency(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    void publishesAndRejectsPendingReviewWithIncrementedVersionAndAudit() {
        String approveRequestId = UUID.randomUUID().toString();
        CollectionRevisionRow pending = revision(11L, 10L, 1, "pending_review", "PRACTICE");
        pending.setRowVersion(6L);
        when(mapper.selectIdempotency("approve_collection", approveRequestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("approve_collection"), eq(approveRequestId), any(), eq(11L), any())).thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of(validItem()));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), null))
            .thenReturn(List.of(questionRevision(21L, "published")));
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.markPublished(11L, 7L)).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            CollectionMutationVo result = service.approve("11", approveRequestId);

            assertThat(result.status()).isEqualTo("published");
            assertThat(result.rowVersion()).isEqualTo("7");
        }
        verify(mapper).upsertCurrent(10L, 11L);
        verify(mapper).insertAudit(anyLong(), eq(7L), eq("approve_collection_review"), eq(11L), any(), any(), any());

        String rejectRequestId = UUID.randomUUID().toString();
        when(mapper.selectIdempotency("reject_collection", rejectRequestId)).thenReturn(null);
        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), eq(rejectRequestId), any(), eq(11L), any())).thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.markRejected(11L, 7L, "依据不足")).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);

            CollectionMutationVo result = service.reject("11", rejectRequestId, "依据不足");

            assertThat(result.status()).isEqualTo("rejected");
            assertThat(result.rowVersion()).isEqualTo("7");
        }
        verify(mapper).insertAudit(anyLong(), eq(7L), eq("reject_collection_review"), eq(11L), any(), any(), any());
    }

    @Test
    void jointSubmitValidatesAndSubmitsDraftQuestionInTheSameUseCase() {
        String requestId = UUID.randomUUID().toString();
        CollectionRevisionRow draft = revision(11L, 10L, 1, "draft", "PRACTICE");
        draft.setQuestionCount(1); draft.setTotalReportScore(BigDecimal.TWO);
        CollectionItemRow item = validItem();
        item.setStatus("draft");
        QuestionRows.Revision question = questionRevision(21L, "draft");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), 7L)).thenReturn(List.of(question));
        when(reviewSubmissionSupport.validate(question)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(requestId), any(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.markSubmitted(11L, 7L)).thenReturn(1);

        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isSuperAdmin).thenReturn(false);
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThat(service.submitReview("11", requestId).status()).isEqualTo("pending_review");
        }

        verify(reviewSubmissionSupport).submitDraft(eq(question), eq(7L), eq(requestId), any());
        verify(mapper).markSubmitted(11L, 7L);
    }

    @Test
    void rejectedCollectionDetailsRemainEditableAndExposeRejectedStatus() {
        CollectionRevisionRow rejected = revision(11L, 10L, 1, "rejected", "PRACTICE");
        rejected.setReviewOpinion("题目审核未通过");
        when(mapper.selectRevision(11L)).thenReturn(rejected);
        when(mapper.selectItems(11L)).thenReturn(List.of());

        var detail = service.revisionDetail("11");

        assertThat(detail.status()).isEqualTo("rejected");
        assertThat(detail.reviewOpinion()).isEqualTo("题目审核未通过");
        assertThat(detail.allowedActions()).containsExactly("view", "edit", "submit_review", "delete");
    }

    @Test
    void writesRollbackForCheckedExceptions() throws Exception {
        assertRollbackFor("create", String.class, org.dromara.certmuse.question.domain.bo.CollectionSaveBo.class);
        assertRollbackFor("save", String.class, String.class,
            org.dromara.certmuse.question.domain.bo.CollectionSaveBo.class);
        assertRollbackFor("rename", String.class, String.class, CollectionRenameBo.class);
        assertRollbackFor("deleteDraft", String.class, String.class);
        assertRollbackFor("createRevision", String.class, String.class, CollectionRevisionCreateBo.class);
        assertRollbackFor("submitReview", String.class, String.class);
        assertRollbackFor("approve", String.class, String.class);
        assertRollbackFor("reject", String.class, String.class, String.class);
        assertRollbackFor("offline", String.class, String.class);
    }

    private static CollectionRevisionRow revision(long id, long collectionId, int revisionNo,
                                                     String status, String type) {
        CollectionRevisionRow row = new CollectionRevisionRow();
        row.setId(id); row.setCollectionId(collectionId); row.setCollectionCode("COL-1");
        row.setRevisionNo(revisionNo); row.setStatus(status); row.setRowVersion(0L);
        row.setCollectionName("题集"); row.setCollectionType(type);
        row.setCertificationId(1L); row.setSyllabusVersionId(2L);
        row.setDurationMinutes(60); row.setQuestionCount(0); row.setTotalReportScore(BigDecimal.ZERO);
        return row;
    }

    private static CollectionSaveBo saveCommand(String rowVersion) {
        CollectionSaveBo command = new CollectionSaveBo();
        command.setCollectionName(" 题集 ");
        command.setCollectionType(" PRACTICE ");
        command.setCertificationId("1");
        command.setSyllabusVersionId("2");
        command.setDurationMinutes(60);
        command.setRowVersion(rowVersion);
        CollectionSaveBo.ItemBo item = new CollectionSaveBo.ItemBo();
        item.setItemOrder(1);
        item.setQuestionRevisionId("21");
        item.setReportScore(BigDecimal.TWO);
        command.setItems(List.of(item));
        return command;
    }

    private static CollectionItemRow metadataItem() {
        CollectionItemRow item = new CollectionItemRow();
        item.setQuestionRevisionId(21L);
        item.setStatus("published");
        item.setCertificationId(1L);
        item.setSyllabusVersionId(2L);
        item.setAnswerSchema("1.0");
        return item;
    }

    private static CollectionItemRow validItem() {
        CollectionItemRow item = new CollectionItemRow();
        item.setItemOrder(1); item.setQuestionRevisionId(21L); item.setReportScore(BigDecimal.TWO);
        item.setStatus("published"); item.setCertificationId(1L); item.setSyllabusVersionId(2L);
        item.setExamSubjectId(3L); item.setKnowledgePointId(4L); item.setQuestionType("CHOICE");
        return item;
    }

    private static QuestionRows.Revision questionRevision(long revisionId, String status) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setQuestionId(20L); row.setRevisionId(revisionId); row.setStatus(status); row.setRowVersion(1L);
        return row;
    }

    private static CollectionListRow listRow(String collectionId, String revisionId, int revisionNo,
                                              String status, boolean hasReviewOpinion, boolean currentPublished) {
        CollectionListRow row = new CollectionListRow();
        row.setCollectionId(collectionId); row.setCollectionCode("COL-1"); row.setRevisionId(revisionId);
        row.setParentCollectionName("父题集");
        row.setCollectionUpdatedTime(OffsetDateTime.parse("2026-08-07T00:00:00+08:00"));
        row.setRevisionNo(revisionNo); row.setCollectionName("V1快照名称"); row.setCollectionType("PRACTICE");
        row.setCertificationId("1"); row.setCertificationName("资格"); row.setSyllabusVersionId("2");
        row.setSyllabusVersionName("考纲"); row.setStatus(status); row.setHasReviewOpinion(hasReviewOpinion);
        row.setCurrentPublished(currentPublished); row.setRowVersion(1L); row.setQuestionCount(1);
        row.setTotalReportScore(BigDecimal.TWO); row.setUpdatedTime(OffsetDateTime.parse("2026-08-06T00:00:00+08:00"));
        return row;
    }

    private static void assertRollbackFor(String methodName, Class<?>... parameters) throws Exception {
        Transactional transactional = CollectionServiceImpl.class.getMethod(methodName, parameters)
            .getAnnotation(Transactional.class);
        assertThat(transactional.rollbackFor()).containsExactly(Exception.class);
    }

    private CollectionPublishCheckVo publishCheck(CollectionRevisionRow revision) throws Exception {
        Method method = CollectionServiceImpl.class.getDeclaredMethod("publishCheck", CollectionRevisionRow.class);
        method.setAccessible(true);
        return (CollectionPublishCheckVo) method.invoke(service, revision);
    }
}
