package org.dromara.certmuse.learning.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.catalog.domain.KnowledgePointDirectoryRow;
import org.dromara.certmuse.catalog.service.KnowledgePointDirectoryCatalogService;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryErrorVo;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointDirectoryVo;
import org.dromara.certmuse.learning.domain.vo.KnowledgePointLookupVo;
import org.dromara.certmuse.learning.service.KnowledgePointDirectoryService;
import org.dromara.certmuse.learning.support.KnowledgePointDirectoryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional U07 implementation that keeps catalog persistence behind its service contract. */
@Service
@RequiredArgsConstructor
public class KnowledgePointDirectoryServiceImpl implements KnowledgePointDirectoryService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgePointDirectoryServiceImpl.class);
    private static final int MAX_IDS = 100;
    private static final String MAX_LONG = "9223372036854775807";

    private final KnowledgePointDirectoryCatalogService catalogService;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public KnowledgePointDirectoryVo lookup(long userId, String rawIds) {
        List<Long> ids = parseIds(rawIds);
        try {
            KnowledgePointDirectoryCatalogService.LookupResult result = catalogService.lookupForCurrentGoal(userId, ids);
            if (!result.currentGoalFound()) {
                throw failure(409, "LEARNING_GOAL_NOT_ACTIVE", "当前没有有效学习目标", false, List.of(), null);
            }
            Map<Long, KnowledgePointDirectoryRow> byId = result.items().stream()
                .collect(java.util.stream.Collectors.toMap(KnowledgePointDirectoryRow::id, row -> row,
                    (first, ignored) -> first));
            List<KnowledgePointLookupVo> items = ids.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(row -> new KnowledgePointLookupVo(Long.toString(row.id()), row.syllabusNumber(), row.syllabusTitle(),
                    Long.toString(row.examSubjectId()), row.examSubjectName()))
                .toList();
            log.info("Knowledge-point directory lookup completed, requestCount={}, hitCount={}, scopeValidationResult={}",
                ids.size(), items.size(), "CURRENT_GOAL_FOUND");
            return new KnowledgePointDirectoryVo(items);
        } catch (KnowledgePointDirectoryException exception) {
            if (exception.status() == 409) {
                log.info("Knowledge-point directory lookup completed, requestCount={}, hitCount={}, scopeValidationResult={}",
                    ids.size(), 0, "CURRENT_GOAL_MISSING");
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(500, "KNOWLEDGE_POINT_LOOKUP_SYSTEM_FAILURE", "知识点目录暂时不可用", true, List.of(), exception);
        }
    }

    private List<Long> parseIds(String rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw invalid("REQUIRED", "ids不能为空");
        }
        String[] parts = rawIds.split(",", -1);
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        for (String part : parts) {
            String id = normalize(part);
            normalized.add(Long.parseLong(id));
        }
        if (normalized.size() > MAX_IDS) {
            throw outOfRange("ids最多包含100个去重后的知识点ID");
        }
        return new ArrayList<>(normalized);
    }

    private String normalize(String value) {
        if (value == null || value.isEmpty() || !value.chars().allMatch(character -> character >= '0' && character <= '9')) {
            throw invalid("INVALID_FORMAT", "ids必须是逗号分隔的正整数列表");
        }
        String normalized = value.replaceFirst("^0+", "");
        if (normalized.isEmpty()) {
            throw outOfRange("ids必须是正整数");
        }
        if (normalized.length() > 19 || normalized.length() == 19 && normalized.compareTo(MAX_LONG) > 0) {
            throw outOfRange("ids超出允许范围");
        }
        return normalized;
    }

    private KnowledgePointDirectoryException invalid(String code, String message) {
        return failure(400, "KNOWLEDGE_POINT_LOOKUP_INVALID", "请求参数不正确", false,
            List.of(new KnowledgePointDirectoryErrorVo.FieldErrorVo("ids", code, message)), null);
    }

    private KnowledgePointDirectoryException outOfRange(String message) {
        return invalid("OUT_OF_RANGE", message);
    }

    private KnowledgePointDirectoryException failure(int status, String errorCode, String message, boolean retryable,
                                                     List<KnowledgePointDirectoryErrorVo.FieldErrorVo> fieldErrors,
                                                     Throwable cause) {
        return new KnowledgePointDirectoryException(status, errorCode, message, retryable, fieldErrors, cause);
    }
}
