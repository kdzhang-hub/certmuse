package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import org.dromara.certmuse.catalog.domain.CmAsyncJob;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ImportJobDispatcherTest {
    @Mock ImportMapper repository;
    @Mock ImportWorker worker;
    @Mock QuestionImportWorker questionWorker;
    @Mock TextbookImportWorker textbookWorker;
    @Mock ImportPersistenceService persistence;
    @Mock ImportStorage storage;
    @Mock ImportProgressPublisher progressPublisher;
    private ImportJobDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ImportJobDispatcher(repository, worker, questionWorker, textbookWorker, persistence, storage,
            JsonMapper.builder().build(), progressPublisher);
    }

    @Test
    void validDispatchesEveryBusinessJobToItsOwnerAndMarksSuccess() {
        CmAsyncJob job = job(ImportProtocol.KNOWLEDGE_PRECHECK_JOB, "101", null);
        when(repository.claimJob(ImportProtocol.KNOWLEDGE_PRECHECK_JOB)).thenReturn(job);

        dispatcher.dispatch();

        verify(worker).validate(101L);
        verify(repository).succeedJob(job.id());
    }

    @Test
    void validFallsThroughPriorityOrderToQuestionPersistence() {
        CmAsyncJob job = job(ImportProtocol.QUESTION_PERSIST_JOB, "202", null);
        when(repository.claimJob(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
            ImportProtocol.QUESTION_PERSIST_JOB.equals(invocation.getArgument(0)) ? job : null);

        dispatcher.dispatch();

        InOrder order = inOrder(repository);
        order.verify(repository).claimJob(ImportProtocol.KNOWLEDGE_PRECHECK_JOB);
        order.verify(repository).claimJob(ImportProtocol.KNOWLEDGE_PERSIST_JOB);
        order.verify(repository).claimJob(ImportProtocol.QUESTION_PRECHECK_JOB);
        order.verify(repository).claimJob(ImportProtocol.QUESTION_PERSIST_JOB);
        verify(questionWorker).persist(202L);
        verify(repository).succeedJob(job.id());
    }

    @Test
    void invalidRetriesFailedPrecheckAndClearsPartialBatchData() {
        CmAsyncJob job = job(ImportProtocol.QUESTION_PRECHECK_JOB, "303", null);
        when(repository.claimJob(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
            ImportProtocol.QUESTION_PRECHECK_JOB.equals(invocation.getArgument(0)) ? job : null);
        when(repository.retryJob(job.id(), "预检任务执行失败")).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("bad archive")).when(questionWorker).validate(303L);

        dispatcher.dispatch();

        verify(repository).clearBatchData(303L);
        verify(repository, never()).failJob(job.id(), "预检任务执行失败");
        verify(repository, never()).succeedJob(job.id());
    }

    @Test
    void invalidPermanentlyFailsExhaustedPersistenceJobAndBatch() {
        CmAsyncJob job = job(ImportProtocol.KNOWLEDGE_PERSIST_JOB, "404", null);
        when(repository.claimJob(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
            ImportProtocol.KNOWLEDGE_PERSIST_JOB.equals(invocation.getArgument(0)) ? job : null);
        when(repository.retryJob(job.id(), "正式导入任务执行失败")).thenReturn(0);
        org.mockito.Mockito.doThrow(new IllegalStateException("database")).when(persistence).persistKnowledgeTree(404L);

        dispatcher.dispatch();

        verify(repository).failJob(job.id(), "正式导入任务执行失败");
        verify(repository).failImporting(org.mockito.ArgumentMatchers.eq(404L), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void edgeReturnsAfterRecoveryWhenThereIsNoClaimableJob() {
        dispatcher.dispatch();

        verify(repository).recoverExpiredJobs();
        verify(repository, never()).succeedJob(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void edgeCleanupDeletesOnlyUnreferencedObjectsWhenReferenceCheckIsEnabled() {
        CmAsyncJob job = job(ImportProtocol.OBJECT_CLEANUP_JOB, "ignored", "{\"object_key\":\"images/a.png\",\"check_references\":true}");
        when(repository.claimJob(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation ->
            ImportProtocol.OBJECT_CLEANUP_JOB.equals(invocation.getArgument(0)) ? job : null);
        when(repository.countImageReferences("images/a.png")).thenReturn(1L);

        dispatcher.dispatch();

        verify(storage, never()).delete("images/a.png");
        verify(repository).succeedJob(job.id());
    }

    private static CmAsyncJob job(String type, String businessKey, String payload) {
        return new CmAsyncJob(1L, type, businessKey, payload, 0, 3);
    }
}
