package org.dromara.certmuse.catalog.support;

import org.dromara.certmuse.catalog.service.ImportProgressPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.certmuse.catalog.domain.CmAsyncJob;
import org.dromara.certmuse.catalog.mapper.ImportMapper;
import org.dromara.certmuse.catalog.service.impl.ImportPersistenceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Dispatches persistent import jobs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportJobDispatcher {

    private final ImportMapper repository;
    private final ImportWorker worker;
    private final QuestionImportWorker questionWorker;
    private final TextbookImportWorker textbookWorker;
    private final ImportPersistenceService persistence;
    private final ImportStorage storage;
    private final JsonMapper objectMapper;
    private final ImportProgressPublisher progressPublisher;

    @Scheduled(fixedDelayString = "${certmuse.import.dispatch-delay-ms:1000}")
    public void dispatch() {
        repository.recoverExpiredJobs();
        CmAsyncJob job = repository.claimJob(ImportProtocol.KNOWLEDGE_PRECHECK_JOB);
        if (job == null) {
            job = repository.claimJob(ImportProtocol.KNOWLEDGE_PERSIST_JOB);
        }
        if (job == null) job = repository.claimJob(ImportProtocol.QUESTION_PRECHECK_JOB);
        if (job == null) job = repository.claimJob(ImportProtocol.QUESTION_PERSIST_JOB);
        if (job == null) job = repository.claimJob(ImportProtocol.PAPER_PRECHECK_JOB);
        if (job == null) job = repository.claimJob(ImportProtocol.PAPER_PERSIST_JOB);
        if (job == null) job = repository.claimJob(ImportProtocol.TEXTBOOK_PRECHECK_JOB);
        if (job == null) job = repository.claimJob(ImportProtocol.TEXTBOOK_PERSIST_JOB);
        if (job == null) {
            job = repository.claimJob(ImportProtocol.OBJECT_CLEANUP_JOB);
        }
        if (job == null) {
            return;
        }
        try {
            if (ImportProtocol.KNOWLEDGE_PRECHECK_JOB.equals(job.jobType())) {
                worker.validate(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.KNOWLEDGE_PERSIST_JOB.equals(job.jobType())) {
                persistence.persistKnowledgeTree(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.QUESTION_PRECHECK_JOB.equals(job.jobType())) {
                questionWorker.validate(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.QUESTION_PERSIST_JOB.equals(job.jobType())) {
                questionWorker.persist(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.PAPER_PRECHECK_JOB.equals(job.jobType())) {
                questionWorker.validate(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.PAPER_PERSIST_JOB.equals(job.jobType())) {
                questionWorker.persist(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.TEXTBOOK_PRECHECK_JOB.equals(job.jobType())) {
                textbookWorker.validate(Long.parseLong(job.businessKey()));
            } else if (ImportProtocol.TEXTBOOK_PERSIST_JOB.equals(job.jobType())) {
                persistence.persistTextbook(Long.parseLong(job.businessKey()));
            } else {
                JsonNode payload = objectMapper.readTree(job.payload());
                String objectKey = payload.path("object_key").stringValue();
                if (!payload.path("check_references").asBoolean(false)
                    || repository.countImageReferences(objectKey) == 0) {
                    storage.delete(objectKey);
                }
            }
            repository.succeedJob(job.id());
            if (!ImportProtocol.OBJECT_CLEANUP_JOB.equals(job.jobType())) {
                progressPublisher.publish(Long.parseLong(job.businessKey()));
            }
        } catch (Exception exception) {
            handleFailure(job, exception);
        }
    }

    private void handleFailure(CmAsyncJob job, Exception exception) {
        boolean persist = ImportProtocol.KNOWLEDGE_PERSIST_JOB.equals(job.jobType())
            || ImportProtocol.QUESTION_PERSIST_JOB.equals(job.jobType())
            || ImportProtocol.PAPER_PERSIST_JOB.equals(job.jobType())
            || ImportProtocol.TEXTBOOK_PERSIST_JOB.equals(job.jobType());
        boolean precheck = ImportProtocol.KNOWLEDGE_PRECHECK_JOB.equals(job.jobType())
            || ImportProtocol.QUESTION_PRECHECK_JOB.equals(job.jobType())
            || ImportProtocol.PAPER_PRECHECK_JOB.equals(job.jobType())
            || ImportProtocol.TEXTBOOK_PRECHECK_JOB.equals(job.jobType());
        String summary = persist ? "正式导入任务执行失败" : "预检任务执行失败";
        String traceId = UUID.randomUUID().toString();
        if (repository.retryJob(job.id(), summary) == 1) {
            if (precheck) {
                repository.clearBatchData(Long.parseLong(job.businessKey()));
            }
        } else {
            repository.failJob(job.id(), summary);
            if (precheck) {
                repository.failActive(Long.parseLong(job.businessKey()), traceId);
            } else if (persist) {
                repository.failImporting(Long.parseLong(job.businessKey()), traceId);
            }
            if (precheck || persist) {
                progressPublisher.publish(Long.parseLong(job.businessKey()));
            }
        }
        log.error(
            "Persistent import job failed, jobId={}, jobType={}, traceId={}",
            job.id(),
            job.jobType(),
            traceId,
            exception
        );
    }
}
