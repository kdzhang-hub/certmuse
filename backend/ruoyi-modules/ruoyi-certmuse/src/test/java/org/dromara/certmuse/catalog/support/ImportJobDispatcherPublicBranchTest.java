package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.domain.CmAsyncJob;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.ImportProgressPublisher;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportJobDispatcherPublicBranchTest {

    @Mock
    private ImportMapper repository;
    @Mock
    private ImportWorker worker;
    @Mock
    private QuestionImportWorker questionWorker;
    @Mock
    private TextbookImportWorker textbookWorker;
    @Mock
    private ImportPersistenceService persistence;
    @Mock
    private ImportStorage storage;
    @Mock
    private ImportProgressPublisher progressPublisher;

    private ImportJobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ImportJobDispatcher(
            repository,
            worker,
            questionWorker,
            textbookWorker,
            persistence,
            storage,
            JsonMapper.builder().build(),
            progressPublisher
        );
    }

    @Test
    void dispatchesEveryRemainingBusinessJobToItsOwnerAndPublishesProgress() {
        List<JobDispatchCase> cases = List.of(
            new JobDispatchCase(ImportProtocol.KNOWLEDGE_PERSIST_JOB, 101L),
            new JobDispatchCase(ImportProtocol.QUESTION_PRECHECK_JOB, 102L),
            new JobDispatchCase(ImportProtocol.PAPER_PRECHECK_JOB, 103L),
            new JobDispatchCase(ImportProtocol.PAPER_PERSIST_JOB, 104L),
            new JobDispatchCase(ImportProtocol.TEXTBOOK_PRECHECK_JOB, 105L),
            new JobDispatchCase(ImportProtocol.TEXTBOOK_PERSIST_JOB, 106L)
        );

        for (JobDispatchCase dispatchCase : cases) {
            CmAsyncJob job = job(dispatchCase.jobType(), dispatchCase.businessId(), null);
            claimOnly(job);

            dispatcher.dispatch();

            verify(repository).succeedJob(job.id());
            verify(progressPublisher).publish(dispatchCase.businessId());
            org.mockito.Mockito.clearInvocations(repository, progressPublisher);
        }

        verify(persistence).persistKnowledgeTree(101L);
        verify(questionWorker).validate(102L);
        verify(questionWorker).validate(103L);
        verify(questionWorker).persist(104L);
        verify(textbookWorker).validate(105L);
        verify(persistence).persistTextbook(106L);
    }

    @Test
    void dispatchPriorityStopsAfterTheFirstClaimedJob() {
        CmAsyncJob knowledge = job(ImportProtocol.KNOWLEDGE_PRECHECK_JOB, 201L, null);
        when(repository.claimJob(ImportProtocol.KNOWLEDGE_PRECHECK_JOB)).thenReturn(knowledge);

        dispatcher.dispatch();

        verify(worker).validate(201L);
        verifyNoInteractions(questionWorker);
        verify(repository, never()).claimJob(ImportProtocol.QUESTION_PRECHECK_JOB);
        verify(repository).succeedJob(knowledge.id());
    }

    @Test
    void cleanupDeletesWhenReferenceCheckIsDisabledWithoutCountingReferences() {
        CmAsyncJob job = job(
            ImportProtocol.OBJECT_CLEANUP_JOB,
            301L,
            "{\"object_key\":\"imports/source.jsonl\",\"check_references\":false}"
        );
        claimOnly(job);

        dispatcher.dispatch();

        verify(storage).delete("imports/source.jsonl");
        verify(repository, never()).countImageReferences(anyString());
        verify(repository).succeedJob(job.id());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void cleanupDeletesAnUnreferencedImageWhenReferenceCheckIsEnabled() {
        CmAsyncJob job = job(
            ImportProtocol.OBJECT_CLEANUP_JOB,
            302L,
            "{\"object_key\":\"images/a.png\",\"check_references\":true}"
        );
        claimOnly(job);
        when(repository.countImageReferences("images/a.png")).thenReturn(0L);

        dispatcher.dispatch();

        verify(repository).countImageReferences("images/a.png");
        verify(storage).delete("images/a.png");
        verify(repository).succeedJob(job.id());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void retryableQuestionPersistenceFailureDoesNotMutateBatchOrPublishProgress() {
        CmAsyncJob job = job(ImportProtocol.QUESTION_PERSIST_JOB, 401L, null);
        claimOnly(job);
        doThrow(new IllegalStateException("database unavailable")).when(questionWorker).persist(401L);
        when(repository.retryJob(job.id(), "正式导入任务执行失败")).thenReturn(1);

        dispatcher.dispatch();

        verify(repository).retryJob(job.id(), "正式导入任务执行失败");
        verify(repository, never()).clearBatchData(anyLong());
        verify(repository, never()).failJob(anyLong(), anyString());
        verify(repository, never()).failImporting(anyLong(), anyString());
        verify(repository, never()).succeedJob(anyLong());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void retryableKnowledgePaperAndTextbookPrechecksClearTheirPartialBatchData() {
        CmAsyncJob knowledge = job(ImportProtocol.KNOWLEDGE_PRECHECK_JOB, 411L, null);
        CmAsyncJob paper = job(ImportProtocol.PAPER_PRECHECK_JOB, 412L, null);
        CmAsyncJob textbook = job(ImportProtocol.TEXTBOOK_PRECHECK_JOB, 413L, null);
        AtomicReference<CmAsyncJob> currentJob = new AtomicReference<>();
        when(repository.claimJob(anyString())).thenAnswer(invocation -> {
            CmAsyncJob current = currentJob.get();
            return current.jobType().equals(invocation.getArgument(0)) ? current : null;
        });
        when(repository.retryJob(anyLong(), eq("预检任务执行失败"))).thenReturn(1);
        doThrow(new IllegalStateException("knowledge invalid")).when(worker).validate(411L);
        doThrow(new IllegalStateException("paper invalid")).when(questionWorker).validate(412L);
        doThrow(new IllegalStateException("textbook invalid")).when(textbookWorker).validate(413L);

        for (CmAsyncJob job : List.of(knowledge, paper, textbook)) {
            currentJob.set(job);
            dispatcher.dispatch();
        }

        verify(repository).clearBatchData(411L);
        verify(repository).clearBatchData(412L);
        verify(repository).clearBatchData(413L);
        verify(repository, times(3)).retryJob(anyLong(), eq("预检任务执行失败"));
        verify(repository, never()).failJob(anyLong(), anyString());
        verifyNoInteractions(progressPublisher);
    }

    @Test
    void exhaustedQuestionPrecheckFailsActiveBatchAndPublishesProgress() {
        CmAsyncJob job = job(ImportProtocol.QUESTION_PRECHECK_JOB, 402L, null);
        claimOnly(job);
        doThrow(new IllegalArgumentException("invalid archive")).when(questionWorker).validate(402L);
        when(repository.retryJob(job.id(), "预检任务执行失败")).thenReturn(0);

        dispatcher.dispatch();

        InOrder order = inOrder(repository);
        order.verify(repository).failJob(job.id(), "预检任务执行失败");
        order.verify(repository).failActive(eq(402L), anyString());
        verify(repository, never()).failImporting(anyLong(), anyString());
        verify(progressPublisher).publish(402L);
    }

    @Test
    void exhaustedPaperAndTextbookPersistenceFailuresFailImportingAndPublish() {
        CmAsyncJob paper = job(ImportProtocol.PAPER_PERSIST_JOB, 403L, null);
        claimOnly(paper);
        doThrow(new IllegalStateException("paper failure")).when(questionWorker).persist(403L);
        when(repository.retryJob(paper.id(), "正式导入任务执行失败")).thenReturn(0);

        dispatcher.dispatch();

        verify(repository).failImporting(eq(403L), anyString());
        verify(progressPublisher).publish(403L);
        org.mockito.Mockito.clearInvocations(repository, progressPublisher);

        CmAsyncJob textbook = job(ImportProtocol.TEXTBOOK_PERSIST_JOB, 404L, null);
        claimOnly(textbook);
        doThrow(new IllegalStateException("textbook failure")).when(persistence).persistTextbook(404L);
        when(repository.retryJob(textbook.id(), "正式导入任务执行失败")).thenReturn(0);

        dispatcher.dispatch();

        verify(repository).failImporting(eq(404L), anyString());
        verify(progressPublisher).publish(404L);
    }

    @Test
    void malformedCleanupPayloadIsRetriedAsNonBusinessFailure() {
        CmAsyncJob job = job(ImportProtocol.OBJECT_CLEANUP_JOB, 501L, "not-json");
        claimOnly(job);
        when(repository.retryJob(job.id(), "预检任务执行失败")).thenReturn(1);

        dispatcher.dispatch();

        verify(repository).retryJob(job.id(), "预检任务执行失败");
        verify(repository, never()).clearBatchData(anyLong());
        verify(repository, never()).failActive(anyLong(), anyString());
        verify(repository, never()).failImporting(anyLong(), anyString());
        verifyNoInteractions(storage, progressPublisher);
    }

    @Test
    void exhaustedCleanupFailureFailsOnlyTheJob() {
        CmAsyncJob job = job(
            ImportProtocol.OBJECT_CLEANUP_JOB,
            502L,
            "{\"object_key\":\"images/a.png\",\"check_references\":false}"
        );
        claimOnly(job);
        doThrow(new IllegalStateException("storage unavailable")).when(storage).delete("images/a.png");
        when(repository.retryJob(job.id(), "预检任务执行失败")).thenReturn(0);

        dispatcher.dispatch();

        verify(repository).failJob(job.id(), "预检任务执行失败");
        verify(repository, never()).failActive(anyLong(), anyString());
        verify(repository, never()).failImporting(anyLong(), anyString());
        verifyNoInteractions(progressPublisher);
    }

    private void claimOnly(CmAsyncJob job) {
        when(repository.claimJob(anyString())).thenAnswer(invocation ->
            job.jobType().equals(invocation.getArgument(0)) ? job : null
        );
    }

    private static CmAsyncJob job(String type, long businessId, String payload) {
        return new CmAsyncJob(businessId + 1_000L, type, Long.toString(businessId), payload, 0, 3);
    }

    private record JobDispatchCase(String jobType, long businessId) {
    }
}
