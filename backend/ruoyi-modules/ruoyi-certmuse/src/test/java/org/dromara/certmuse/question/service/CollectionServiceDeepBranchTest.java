package org.dromara.certmuse.question.service;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.certmuse.question.domain.CollectionIdempotencyRow;
import org.dromara.certmuse.question.domain.CollectionItemRow;
import org.dromara.certmuse.question.domain.CollectionListRow;
import org.dromara.certmuse.question.domain.CollectionRevisionRow;
import org.dromara.certmuse.question.domain.QuestionRows;
import org.dromara.certmuse.question.domain.bo.CollectionQueryBo;
import org.dromara.certmuse.question.domain.bo.CollectionRevisionCreateBo;
import org.dromara.certmuse.question.domain.bo.CollectionSaveBo;
import org.dromara.certmuse.question.domain.vo.CollectionPublishCheckVo;
import org.dromara.certmuse.question.domain.vo.QuestionErrorVo;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises collection use cases through the public service boundary. Persistence and the joint question-review
 * collaborator are mocked as system boundaries; no private implementation method is invoked directly.
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class CollectionServiceDeepBranchTest {

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
    void managementListUsesDefaultsAndBuildsOnlyCollectionsThatHaveVisibleRevisions() {
        OffsetDateTime firstUpdate = OffsetDateTime.parse("2026-08-12T09:00:00+08:00");
        OffsetDateTime olderUpdate = firstUpdate.minusDays(1);
        OffsetDateTime latestUpdate = firstUpdate.plusHours(2);
        CollectionListRow draft = listRow("10", "11", 3, "draft", false, false, firstUpdate);
        CollectionListRow published = listRow("10", "9", 1, "published", false, true, olderUpdate);
        CollectionListRow rejected = listRow("10", "12", 4, "rejected", true, false, latestUpdate);
        CollectionQueryBo query = new CollectionQueryBo();
        when(mapper.selectManagedCollectionIds(query, null, null, 20, 0L)).thenReturn(List.of(10L, 20L));
        when(mapper.selectManagedCollectionRevisions(List.of(10L, 20L)))
            .thenReturn(List.of(draft, published, rejected));
        when(mapper.countManagedCollections(query, null, null)).thenReturn(2L);

        var page = service.manageList(query);

        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getRows()).singleElement().satisfies(row -> {
            assertThat(row.collectionId()).isEqualTo("10");
            assertThat(row.currentPublishedRevisionId()).isEqualTo("9");
            assertThat(row.currentPublishedRevisionNo()).isEqualTo(1);
            assertThat(row.updatedTime()).isEqualTo(latestUpdate);
            assertThat(row.revisions()).hasSize(3);
        });
    }

    @Test
    void managementListRejectsEveryPaginationBoundaryAndUnknownStatus() {
        CollectionQueryBo zeroPage = new CollectionQueryBo();
        zeroPage.setPageNum(0);
        assertInvalid(() -> service.manageList(zeroPage), "分页参数超出允许范围");

        CollectionQueryBo zeroSize = new CollectionQueryBo();
        zeroSize.setPageSize(0);
        assertInvalid(() -> service.manageList(zeroSize), "分页参数超出允许范围");

        CollectionQueryBo excessiveSize = new CollectionQueryBo();
        excessiveSize.setPageSize(101);
        assertInvalid(() -> service.manageList(excessiveSize), "分页参数超出允许范围");

        CollectionQueryBo unknownStatus = new CollectionQueryBo();
        unknownStatus.setStatuses(List.of("draft", "unknown"));
        assertInvalid(() -> service.manageList(unknownStatus), "statuses非法");
    }

    @Test
    void detailMapsNullableItemFieldsAndEveryActionState() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "PRACTICE");
        draft.setPauseAllowed(null);
        CollectionItemRow nullableItem = validItem(1, 21L, "published");
        nullableItem.setQuestionId(20L);
        nullableItem.setExamSubjectId(null);
        nullableItem.setKnowledgePointId(null);
        CollectionRevisionRow rejectedHistory = revision(12L, 10L, "rejected", "PRACTICE");
        rejectedHistory.setReviewOpinion("   ");
        when(mapper.selectDisplayRevision(10L)).thenReturn(draft);
        when(mapper.selectRevisions(10L)).thenReturn(List.of(draft, rejectedHistory));
        when(mapper.selectItems(11L)).thenReturn(List.of(nullableItem));

        var draftDetail = service.detail("10");

        assertThat(draftDetail.currentPublishedRevision()).isNull();
        assertThat(draftDetail.allowedActions()).containsExactly("view", "edit", "submit_review", "delete");
        assertThat(draftDetail.displayRevision().pauseAllowed()).isFalse();
        assertThat(draftDetail.displayRevision().items()).singleElement().satisfies(item -> {
            assertThat(item.questionId()).isEqualTo("20");
            assertThat(item.examSubjectId()).isNull();
            assertThat(item.knowledgePointId()).isNull();
        });
        assertThat(draftDetail.revisions().get(1).hasReviewOpinion()).isFalse();

        CollectionRevisionRow published = revision(21L, 20L, "published", "PRACTICE");
        published.setPauseAllowed(true);
        CollectionItemRow identifiedItem = validItem(1, 31L, "published");
        identifiedItem.setExamSubjectId(3L);
        identifiedItem.setKnowledgePointId(4L);
        when(mapper.selectDisplayRevision(20L)).thenReturn(published);
        when(mapper.selectCurrentPublished(20L)).thenReturn(published);
        when(mapper.selectPendingReviewRevision(20L)).thenReturn(revision(22L, 20L, "pending_review", "PRACTICE"));
        when(mapper.selectRevisions(20L)).thenReturn(List.of(published));
        when(mapper.selectItems(21L)).thenReturn(List.of(identifiedItem));

        var publishedDetail = service.detail("20");

        assertThat(publishedDetail.currentPublishedRevision().currentPublished()).isTrue();
        assertThat(publishedDetail.allowedActions()).containsExactly("view");
        assertThat(publishedDetail.displayRevision().pauseAllowed()).isTrue();
        assertThat(publishedDetail.displayRevision().items()).singleElement().satisfies(item -> {
            assertThat(item.examSubjectId()).isEqualTo("3");
            assertThat(item.knowledgePointId()).isEqualTo("4");
        });

        CollectionRevisionRow pending = revision(31L, 30L, "pending_review", "PRACTICE");
        when(mapper.selectRevision(31L)).thenReturn(pending);
        when(mapper.selectItems(31L)).thenReturn(List.of());
        assertThat(service.revisionDetail("31").allowedActions()).containsExactly("view");

        CollectionRevisionRow unknown = revision(41L, 40L, "archived", "PRACTICE");
        when(mapper.selectRevision(41L)).thenReturn(unknown);
        when(mapper.selectItems(41L)).thenReturn(List.of());
        assertThat(service.revisionDetail("41").allowedActions()).containsExactly("view");

        CollectionRevisionRow rejected = revision(51L, 50L, "rejected", "PRACTICE");
        rejected.setReviewOpinion("  信息不足  ");
        when(mapper.selectRevision(51L)).thenReturn(rejected);
        when(mapper.selectItems(51L)).thenReturn(List.of());
        assertThat(service.revisionDetail("51").reviewOpinion()).isEqualTo("信息不足");
    }

    @Test
    void createRejectsAllBasicFieldBoundariesBeforePersistence() {
        CollectionSaveBo nullName = validCommand(false);
        nullName.setCollectionName(null);
        assertInvalid(() -> service.create(requestId(), nullName), "题集名称不能为空且不能超过200字符");

        CollectionSaveBo longName = validCommand(false);
        longName.setCollectionName("题".repeat(201));
        assertInvalid(() -> service.create(requestId(), longName), "题集名称不能为空且不能超过200字符");

        CollectionSaveBo unsupportedType = validCommand(false);
        unsupportedType.setCollectionType("UNSUPPORTED");
        assertInvalid(() -> service.create(requestId(), unsupportedType), "collectionType非法");

        CollectionSaveBo nullDuration = validCommand(false);
        nullDuration.setDurationMinutes(null);
        assertInvalid(() -> service.create(requestId(), nullDuration), "durationMinutes必须大于0");

        CollectionSaveBo zeroDuration = validCommand(false);
        zeroDuration.setDurationMinutes(0);
        assertInvalid(() -> service.create(requestId(), zeroDuration), "durationMinutes必须大于0");

        CollectionSaveBo nullItems = validCommand(false);
        nullItems.setItems(null);
        assertInvalid(() -> service.create(requestId(), nullItems), "items不能为空");

        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void saveRequiresAValidNonNegativeRowVersion() {
        CollectionSaveBo missing = validCommand(false);
        assertInvalid(() -> service.save("11", requestId(), missing), "保存草稿必须提供rowVersion");

        CollectionSaveBo blank = validCommand(true);
        blank.setRowVersion("   ");
        assertInvalid(() -> service.save("11", requestId(), blank), "保存草稿必须提供rowVersion");

        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(revision(11L, 10L, "draft", "PRACTICE"));

        CollectionSaveBo negative = validCommand(true);
        negative.setRowVersion("-1");
        assertInvalid(() -> service.save("11", requestId(), negative), "rowVersion必须是非负整数");

        CollectionSaveBo nonNumeric = validCommand(true);
        nonNumeric.setRowVersion("not-a-number");
        assertInvalid(() -> service.save("11", requestId(), nonNumeric), "rowVersion必须是非负整数");
    }

    @Test
    void createValidatesItemOrderIdentifiersMetadataScopeAndAnswerSchema() {
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), anyString(), anyString(), eq(null), any()))
            .thenReturn(1);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);

        CollectionSaveBo wrongOrder = validCommand(false);
        wrongOrder.getItems().getFirst().setItemOrder(2);
        assertInvalid(() -> service.create(requestId(), wrongOrder), "题序必须从1连续");

        CollectionSaveBo badIdentifier = validCommand(false);
        badIdentifier.getItems().getFirst().setQuestionRevisionId("0");
        assertInvalid(() -> service.create(requestId(), badIdentifier), "questionRevisionId必须是十进制正整数");

        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any())).thenReturn(List.of());
        assertInvalid(() -> service.create(requestId(), validCommand(false)), "存在无效的题目修订");

        CollectionItemRow nullSchema = metadata(21L, "published", null, 1L, 2L);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any())).thenReturn(List.of(nullSchema));
        assertInvalid(() -> service.create(requestId(), validCommand(false)), "题目修订缺少答题结构");

        CollectionItemRow blankSchema = metadata(21L, "published", "  ", 1L, 2L);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any())).thenReturn(List.of(blankSchema));
        assertInvalid(() -> service.create(requestId(), validCommand(false)), "题目修订缺少答题结构");

        CollectionItemRow otherCertification = metadata(21L, "published", "answer/1.0", 9L, 2L);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any()))
            .thenReturn(List.of(otherCertification));
        assertInvalid(() -> service.create(requestId(), validCommand(false)), "题目修订不属于题集资格或考纲");

        CollectionItemRow otherSyllabus = metadata(21L, "published", "answer/1.0", 1L, 9L);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any())).thenReturn(List.of(otherSyllabus));
        assertInvalid(() -> service.create(requestId(), validCommand(false)), "题目修订不属于题集资格或考纲");
    }

    @Test
    void createAcceptsAnEmptyItemListAndQualificationScopedQuestionWithoutSyllabus() {
        when(mapper.insertIdempotency(anyLong(), eq("create_collection"), anyString(), anyString(), eq(null), any()))
            .thenReturn(1);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertCollection(anyLong(), eq(1L), eq(2L), anyString(), eq("题集"), eq("PRACTICE"), any(), any(), any(), any()))
            .thenReturn(1);

        CollectionSaveBo empty = validCommand(false);
        empty.setItems(List.of());
        assertThat(service.create(requestId(), empty).status()).isEqualTo("draft");

        CollectionItemRow qualificationScoped = metadata(21L, "published", "answer/1.0", 1L, null);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any()))
            .thenReturn(List.of(qualificationScoped));
        assertThat(service.create(requestId(), validCommand(false)).status()).isEqualTo("draft");
    }

    @Test
    void saveDistinguishesIllegalStatusMissingConcurrentRevisionAndVersionRace() {
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        CollectionRevisionRow published = revision(11L, 10L, "published", "PRACTICE");
        when(mapper.lockRevision(11L)).thenReturn(published);
        assertConflict(() -> service.save("11", requestId(), validCommand(true)), "COLLECTION_REVISION_STATE_CONFLICT");

        reset(mapper);
        prepareValidSave();
        when(mapper.updateDraft(eq(11L), eq(0L), anyString(), eq("PRACTICE"), eq(1L), eq(2L), eq(60), eq(1),
            eq(BigDecimal.TWO))).thenReturn(0);
        assertNotFound(() -> service.save("11", requestId(), validCommand(true)), "COLLECTION_REVISION_NOT_FOUND");

        reset(mapper);
        prepareValidSave();
        CollectionRevisionRow current = revision(11L, 10L, "draft", "PRACTICE");
        current.setRowVersion(4L);
        when(mapper.updateDraft(eq(11L), eq(0L), anyString(), eq("PRACTICE"), eq(1L), eq(2L), eq(60), eq(1),
            eq(BigDecimal.TWO))).thenReturn(0);
        when(mapper.selectRevision(11L)).thenReturn(current);
        assertConflict(() -> service.save("11", requestId(), validCommand(true)),
            "COLLECTION_REVISION_VERSION_CONFLICT");
    }

    @Test
    void createRevisionCopiesEveryItemFromTheChosenPublicHistory() {
        CollectionRevisionRow source = revision(11L, 10L, "published", "PRACTICE");
        CollectionRevisionCreateBo command = new CollectionRevisionCreateBo();
        command.setSourceRevisionId("11");
        CollectionItemRow item = validItem(1, 21L, "published");
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.selectRevision(11L)).thenReturn(source);
        when(mapper.insertIdempotency(anyLong(), eq("create_collection_revision"), anyString(), anyString(), eq(10L), any()))
            .thenReturn(1);
        when(mapper.nextRevisionNo(10L)).thenReturn(2);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));

        var result = service.createRevision("10", requestId(), command);

        assertThat(result.createdRevision()).isTrue();
        assertThat(result.sourceRevisionId()).isEqualTo("11");
        verify(mapper).insertItem(anyLong(), anyLong(), eq(21L), eq(1), eq(BigDecimal.TWO), eq("answer/1.0"));
    }

    @Test
    void submitReviewReturnsAllCollectionAndQuestionDiagnosticsThroughThePublicUseCase() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "INVALID");
        draft.setCollectionName(null);
        List<CollectionItemRow> items = new ArrayList<>();

        CollectionItemRow first = validItem(2, 21L, "rejected");
        first.setReportScore(null);
        first.setCertificationId(9L);
        first.setQuestionType("CASE");
        first.setExamSubjectId(null);
        items.add(first);

        CollectionItemRow duplicate = validItem(2, 21L, "pending_review");
        duplicate.setReportScore(BigDecimal.ZERO);
        duplicate.setSyllabusVersionId(9L);
        duplicate.setQuestionType("ESSAY");
        duplicate.setExamSubjectId(2L);
        items.add(duplicate);

        CollectionItemRow archived = validItem(3, 23L, "archived");
        archived.setSyllabusVersionId(null);
        archived.setQuestionType("CASE");
        archived.setKnowledgePointId(9L);
        archived.setExamSubjectId(3L);
        items.add(archived);

        CollectionItemRow invalidScoringLink = validItem(4, 24L, "draft");
        invalidScoringLink.setQuestionType("ESSAY");
        invalidScoringLink.setKnowledgePointId(9L);
        invalidScoringLink.setExamSubjectId(1L);
        items.add(invalidScoringLink);

        CollectionItemRow unreviewedStatus = validItem(5, 25L, "published");
        items.add(unreviewedStatus);
        CollectionItemRow hidden = validItem(6, 26L, "draft");
        items.add(hidden);

        QuestionRows.Revision rejectedQuestion = question(21L, "rejected");
        QuestionRows.Revision pendingQuestion = question(23L, "pending_review");
        QuestionRows.Revision draftQuestion = question(24L, "draft");
        QuestionRows.Revision archivedQuestion = question(25L, "archived");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(items);
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L, 23L, 24L, 25L, 26L), null))
            .thenReturn(List.of(rejectedQuestion, pendingQuestion, draftQuestion, archivedQuestion));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(0);
        when(reviewSubmissionSupport.validate(rejectedQuestion)).thenReturn(List.of());
        when(reviewSubmissionSupport.validate(draftQuestion)).thenReturn(List.of(
            new QuestionErrorVo.BlockingIssueVo("NO_PATH", null, "缺少字段"),
            new QuestionErrorVo.BlockingIssueVo("BLANK_PATH", "   ", "字段为空"),
            new QuestionErrorVo.BlockingIssueVo("WITH_PATH", "answer", "答案错误")));

        assertThatThrownBy(() -> service.submitReview("11", requestId()))
            .isInstanceOfSatisfying(CollectionException.class, exception -> {
                assertThat(exception.getStatus()).isEqualTo(422);
                assertThat(exception.getData().blockingIssues())
                    .extracting(CollectionPublishCheckVo.IssueVo::code)
                    .contains("COLLECTION_NAME_REQUIRED", "COLLECTION_TYPE_INVALID", "COLLECTION_SCOPE_INVALID",
                        "ITEM_ORDER_INVALID", "QUESTION_REVISION_DUPLICATED", "REPORT_SCORE_INVALID",
                        "QUESTION_NOT_PUBLISHED", "QUESTION_SCOPE_MISMATCH",
                        "QUESTION_REVISION_NOT_VISIBLE", "NO_PATH", "BLANK_PATH", "WITH_PATH");
                assertThat(exception.getData().blockingIssues())
                    .extracting(CollectionPublishCheckVo.IssueVo::message)
                    .contains("缺少字段", "字段为空", "answer：答案错误");
            });
        verify(mapper, never()).insertIdempotency(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void submitReviewChecksSyllabusMembershipWhenCertificationExists() {
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "PRACTICE");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(0);

        assertPublishFailure(() -> service.submitReview("11", requestId()),
            "COLLECTION_SCOPE_INVALID", "COLLECTION_QUESTION_REQUIRED");
    }

    @Test
    void submitReviewAcceptsATenQuestionFirstDiagnostic() {
        CollectionRevisionRow diagnostic = revision(11L, 10L, "draft", "FIRST_DIAGNOSTIC");
        diagnostic.setQuestionCount(10);
        diagnostic.setTotalReportScore(BigDecimal.TEN);
        List<CollectionItemRow> items = new ArrayList<>();
        List<QuestionRows.Revision> questions = new ArrayList<>();
        List<Long> questionIds = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            long questionId = 100L + index;
            CollectionItemRow item = validItem(index, questionId, "published");
            item.setReportScore(BigDecimal.ONE);
            item.setExamSubjectId((long) ((index - 1) % 3 + 1));
            item.setEstimatedSeconds(60);
            item.setInvalidKnowledgeCount(0);
            item.setMissingLeafImportanceCount(0);
            items.add(item);
            questions.add(question(questionId, "published"));
            questionIds.add(questionId);
        }
        String requestId = requestId();
        when(mapper.selectRevision(11L)).thenReturn(diagnostic);
        when(mapper.selectItems(11L)).thenReturn(items);
        when(reviewSubmissionSupport.lockVisibleRevisions(questionIds, null)).thenReturn(questions);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(diagnostic);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectActiveSubjectIds(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(mapper.insertIdempotency(anyLong(), eq("submit_collection_review"), eq(requestId), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.markSubmitted(11L, null)).thenReturn(1);

        var result = service.submitReview("11", requestId);

        assertThat(result.status()).isEqualTo("pending_review");
        verify(mapper).markSubmitted(11L, null);
    }

    @Test
    void submitReviewDetectsConcurrentItemCountChange() {
        CollectionItemRow item = validItem(1, 21L, "published");
        assertConcurrentChange(List.of(item), List.of(item, validItem(2, 22L, "published")));
    }

    @Test
    void submitReviewDetectsConcurrentItemOrderChange() {
        CollectionItemRow before = validItem(1, 21L, "published");
        CollectionItemRow after = validItem(2, 21L, "published");
        assertConcurrentChange(List.of(before), List.of(after));
    }

    @Test
    void submitReviewDetectsConcurrentScoreChange() {
        CollectionItemRow before = validItem(1, 21L, "published");
        CollectionItemRow after = validItem(1, 21L, "published");
        after.setReportScore(BigDecimal.TEN);
        assertConcurrentChange(List.of(before), List.of(after));
    }

    @Test
    void submitReviewDetectsConcurrentAnswerSchemaChange() {
        CollectionItemRow before = validItem(1, 21L, "published");
        CollectionItemRow after = validItem(1, 21L, "published");
        after.setAnswerSchema("answer/2.0");
        assertConcurrentChange(List.of(before), List.of(after));
    }

    @Test
    void approvalReportsEveryQuestionPublicationState() {
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review", "PRACTICE");
        List<CollectionItemRow> items = List.of(
            validItem(1, 21L, "rejected"),
            validItem(2, 22L, "pending_review"),
            validItem(3, 23L, "archived"));
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(items);
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L, 22L, 23L), null))
            .thenReturn(List.of(question(21L, "rejected"), question(22L, "pending_review"), question(23L, "archived")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);

        assertPublishFailure(() -> service.approve("11", requestId()),
            "QUESTION_REJECTED", "QUESTION_PENDING_REVIEW", "QUESTION_NOT_PUBLISHED");
    }

    @Test
    void approvalDistinguishesMissingCollectionAndLockedRevisionStates() {
        assertNotFound(() -> service.approve("11", requestId()), "COLLECTION_REVISION_NOT_FOUND");

        reset(mapper, reviewSubmissionSupport);
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review", "PRACTICE");
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn((Long) null);
        assertNotFound(() -> service.approve("11", requestId()), "COLLECTION_NOT_FOUND");

        reset(mapper, reviewSubmissionSupport);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        assertConflict(() -> service.approve("11", requestId()), "COLLECTION_REVISION_STATE_CONFLICT");

        reset(mapper, reviewSubmissionSupport);
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "PRACTICE");
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);
        assertConflict(() -> service.approve("11", requestId()), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void approvalReplaysARequestCompletedAfterTheAggregateWasLocked() {
        String requestId = requestId();
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review", "PRACTICE");
        CollectionIdempotencyRow replay = idempotency(DigestUtil.sha256Hex("approve:11"), 11L, successEnvelope("published"));
        when(mapper.selectIdempotency("approve_collection", requestId)).thenReturn(null, replay);
        when(mapper.selectRevision(11L)).thenReturn(pending);
        when(mapper.selectItems(11L)).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);

        assertThat(service.approve("11", requestId).status()).isEqualTo("published");
        verify(mapper, never()).markPublished(anyLong(), any());
    }

    @Test
    void approvalReportsARevisionDeletedDuringFinalPublication() {
        String requestId = requestId();
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review", "PRACTICE");
        CollectionItemRow item = validItem(1, 21L, "published");
        when(mapper.selectRevision(11L)).thenReturn(pending).thenReturn((CollectionRevisionRow) null);
        when(mapper.selectItems(11L)).thenReturn(List.of(item));
        when(reviewSubmissionSupport.lockVisibleRevisions(List.of(21L), null))
            .thenReturn(List.of(question(21L, "published")));
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.insertIdempotency(anyLong(), eq("approve_collection"), eq(requestId), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.markPublished(11L, null)).thenReturn(0);

        assertNotFound(() -> service.approve("11", requestId), "COLLECTION_REVISION_NOT_FOUND");
    }

    @Test
    void offlineRejectsMissingAggregateAndEachInvalidCurrentState() {
        when(mapper.insertIdempotency(anyLong(), eq("offline_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        assertNotFound(() -> service.offline("11", requestId()), "COLLECTION_REVISION_NOT_FOUND");

        reset(mapper);
        prepareOffline(revision(11L, 10L, "published", "PRACTICE"), null);
        when(mapper.lockCollection(10L)).thenReturn(null);
        assertNotFound(() -> service.offline("11", requestId()), "COLLECTION_NOT_FOUND");

        reset(mapper);
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "PRACTICE");
        prepareOffline(draft, draft);
        assertConflict(() -> service.offline("11", requestId()), "COLLECTION_OFFLINE_INVALID_STATE");

        reset(mapper);
        CollectionRevisionRow published = revision(11L, 10L, "published", "PRACTICE");
        prepareOffline(published, null);
        assertConflict(() -> service.offline("11", requestId()), "COLLECTION_OFFLINE_INVALID_STATE");
    }

    @Test
    void offlineReportsRevisionDeletedDuringTheFinalStateTransition() {
        CollectionRevisionRow published = revision(11L, 10L, "published", "PRACTICE");
        prepareOffline(published, published);
        when(mapper.selectRevision(11L)).thenReturn(published).thenReturn((CollectionRevisionRow) null);
        when(mapper.deleteCurrent(10L, 11L)).thenReturn(1);
        when(mapper.markPublishedAsDraft(11L)).thenReturn(0);

        assertNotFound(() -> service.offline("11", requestId()), "COLLECTION_REVISION_NOT_FOUND");
    }

    @Test
    void rejectValidatesOpinionAndLockedRevisionStates() {
        assertInvalid(() -> service.reject("11", requestId(), null), "审核意见不能为空且不能超过500字符");
        assertInvalid(() -> service.reject("11", requestId(), "   "), "审核意见不能为空且不能超过500字符");

        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        assertNotFound(() -> service.reject("11", requestId(), "依据不足"), "COLLECTION_REVISION_NOT_FOUND");

        reset(mapper);
        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(revision(11L, 10L, "draft", "PRACTICE"));
        assertConflict(() -> service.reject("11", requestId(), "依据不足"), "COLLECTION_REVISION_STATE_CONFLICT");
    }

    @Test
    void rejectTrimsOpinionAndReportsARevisionDeletedDuringTransition() {
        CollectionRevisionRow pending = revision(11L, 10L, "pending_review", "PRACTICE");
        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.markRejected(11L, null, "依据不足")).thenReturn(1);

        assertThat(service.reject("11", requestId(), "  依据不足  ").status()).isEqualTo("rejected");
        verify(mapper).markRejected(11L, null, "依据不足");

        reset(mapper);
        when(mapper.insertIdempotency(anyLong(), eq("reject_collection"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(pending);
        when(mapper.markRejected(11L, null, "内容错误")).thenReturn(0);
        assertNotFound(() -> service.reject("11", requestId(), "内容错误"), "COLLECTION_REVISION_NOT_FOUND");
    }

    private void prepareValidSave() {
        when(mapper.insertIdempotency(anyLong(), eq("save_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.lockRevision(11L)).thenReturn(revision(11L, 10L, "draft", "PRACTICE"));
        when(mapper.countCertification(1L)).thenReturn(1);
        when(mapper.countSyllabusInCertification(2L, 1L)).thenReturn(1);
        when(mapper.selectQuestionRevisionMetadata(eq(java.util.Set.of(21L)), any()))
            .thenReturn(List.of(metadata(21L, "published", "answer/1.0", 1L, 2L)));
    }

    private void assertConcurrentChange(List<CollectionItemRow> before, List<CollectionItemRow> after) {
        CollectionRevisionRow draft = revision(11L, 10L, "draft", "PRACTICE");
        when(mapper.selectRevision(11L)).thenReturn(draft);
        when(mapper.selectItems(11L)).thenReturn(before, after);
        when(reviewSubmissionSupport.lockVisibleRevisions(any(), any())).thenReturn(List.of());
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(draft);

        assertConflict(() -> service.submitReview("11", requestId()), "COLLECTION_ITEMS_CONCURRENTLY_CHANGED");
        verify(mapper, never()).markSubmitted(anyLong(), any());
    }

    private void prepareOffline(CollectionRevisionRow revision, CollectionRevisionRow current) {
        when(mapper.insertIdempotency(anyLong(), eq("offline_collection_revision"), anyString(), anyString(), eq(11L), any()))
            .thenReturn(1);
        when(mapper.selectRevision(11L)).thenReturn(revision);
        when(mapper.lockCollection(10L)).thenReturn(10L);
        when(mapper.lockRevision(11L)).thenReturn(revision);
        when(mapper.lockCurrentPublished(10L)).thenReturn(current);
    }

    private void assertPublishFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String... codes) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CollectionException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(422);
            assertThat(exception.getData().errorCode()).isEqualTo("COLLECTION_PUBLISH_CHECK_FAILED");
            assertThat(exception.getData().blockingIssues()).extracting(CollectionPublishCheckVo.IssueVo::code)
                .contains(codes);
        });
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
        command.setItems(new ArrayList<>(List.of(commandItem(1, "21", BigDecimal.TWO))));
        return command;
    }

    private static CollectionSaveBo.ItemBo commandItem(int order, String revisionId, BigDecimal score) {
        CollectionSaveBo.ItemBo item = new CollectionSaveBo.ItemBo();
        item.setItemOrder(order);
        item.setQuestionRevisionId(revisionId);
        item.setReportScore(score);
        return item;
    }

    private static CollectionItemRow metadata(long revisionId, String status, String schema,
                                               Long certificationId, Long syllabusId) {
        CollectionItemRow item = new CollectionItemRow();
        item.setQuestionRevisionId(revisionId);
        item.setStatus(status);
        item.setAnswerSchema(schema);
        item.setCertificationId(certificationId);
        item.setSyllabusVersionId(syllabusId);
        return item;
    }

    private static CollectionItemRow validItem(int order, long revisionId, String status) {
        CollectionItemRow item = metadata(revisionId, status, "answer/1.0", 1L, 2L);
        item.setItemOrder(order);
        item.setQuestionId(revisionId - 1);
        item.setQuestionCode("Q-" + revisionId);
        item.setStem("题干");
        item.setQuestionType("CHOICE");
        item.setDifficulty("MEDIUM");
        item.setExamSubjectId(3L);
        item.setExamSubjectName("科目");
        item.setKnowledgePointId(4L);
        item.setKnowledgePointLabel("知识点");
        item.setReportScore(BigDecimal.TWO);
        return item;
    }

    private static CollectionRevisionRow revision(long id, long collectionId, String status, String type) {
        CollectionRevisionRow row = new CollectionRevisionRow();
        row.setId(id);
        row.setCollectionId(collectionId);
        row.setCollectionCode("COL-1");
        row.setRevisionNo(1);
        row.setStatus(status);
        row.setRowVersion(0L);
        row.setCollectionName("题集");
        row.setCollectionType(type);
        row.setCertificationId(1L);
        row.setCertificationName("资格");
        row.setSyllabusVersionId(2L);
        row.setSyllabusVersionName("考纲");
        row.setDurationMinutes(60);
        row.setQuestionCount(1);
        row.setTotalReportScore(BigDecimal.TWO);
        row.setUpdatedTime(OffsetDateTime.parse("2026-08-12T09:00:00+08:00"));
        return row;
    }

    private static QuestionRows.Revision question(long revisionId, String status) {
        QuestionRows.Revision row = new QuestionRows.Revision();
        row.setQuestionId(revisionId - 1);
        row.setRevisionId(revisionId);
        row.setStatus(status);
        row.setRowVersion(1L);
        return row;
    }

    private static CollectionListRow listRow(String collectionId, String revisionId, int revisionNo,
                                              String status, boolean hasOpinion, boolean current,
                                              OffsetDateTime updatedTime) {
        CollectionListRow row = new CollectionListRow();
        row.setCollectionId(collectionId);
        row.setCollectionCode("COL-1");
        row.setRevisionId(revisionId);
        row.setRevisionNo(revisionNo);
        row.setCollectionName("题集");
        row.setCollectionType("PRACTICE");
        row.setCertificationId("1");
        row.setCertificationName("资格");
        row.setSyllabusVersionId("2");
        row.setSyllabusVersionName("考纲");
        row.setStatus(status);
        row.setCurrentPublished(current);
        row.setHasReviewOpinion(hasOpinion);
        row.setRowVersion(1L);
        row.setQuestionCount(1);
        row.setTotalReportScore(BigDecimal.TWO);
        row.setDurationMinutes(60);
        row.setUpdatedTime(updatedTime);
        return row;
    }

    private static CollectionIdempotencyRow idempotency(String payloadHash, Long resourceId, String response) {
        CollectionIdempotencyRow row = new CollectionIdempotencyRow();
        row.setPayloadHash(payloadHash);
        row.setResourceId(resourceId);
        row.setStatus("succeeded");
        row.setResponseBody(response);
        return row;
    }

    private static String successEnvelope(String status) {
        return """
            {"schema_version":"1.0","response":{"code":200,"msg":"ok","data":{
              "collectionId":"10","collectionCode":"COL-1","revisionId":"11","revisionNo":1,
              "status":"%s","rowVersion":"1","createdRevision":false,"sourceRevisionId":null}}}
            """.formatted(status);
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
