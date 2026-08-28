package org.dromara.certmuse.assessment.service;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.assessment.domain.KnowledgePracticeItemRow;
import org.dromara.certmuse.assessment.mapper.KnowledgePracticeMapper;
import org.dromara.certmuse.learning.service.MistakeFactRecorder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared wrong-answer and profile settlement for synchronously graded choice answers. */
@Service
@RequiredArgsConstructor
public class ChoiceAnswerSettlementService {
    private final KnowledgePracticeMapper mapper;
    private final MistakeFactRecorder mistakeFactRecorder;
    private final JsonMapper jsonMapper;

    public void settle(KnowledgePracticeItemRow item, long attemptId, boolean correct, String requestId) throws Exception {
        for (JsonNode leaf : jsonMapper.readTree(item.getKnowledgeSnapshot()).path("items")) {
            long leafId = Long.parseLong(leaf.path("knowledgePointId").asText());
            if (!correct) mistakeFactRecorder.record(item.getUserId(), item.getGoalId(), attemptId, leafId,
                item.getEvidenceGroupKey());
            Long settlementId = mapper.insertSingleSettlement(IdUtil.getSnowflakeNextId(), item, attemptId,
                leafId, correct, requestId + "-" + leafId);
            mapper.insertPracticeEvidence(IdUtil.getSnowflakeNextId(), settlementId, item, attemptId, leafId, correct);
            mapper.upsertPracticeKnowledgeProfile(IdUtil.getSnowflakeNextId(), item, leafId, correct);
            mapper.insertPracticeKnowledgeChange(IdUtil.getSnowflakeNextId(), item, settlementId, leafId,
                requestId + "-profile-" + leafId);
        }
        mapper.selectParentAggregates(item.getUserId(), item.getGoalId()).forEach(aggregate ->
            mapper.upsertParentProfile(IdUtil.getSnowflakeNextId(), item.getUserId(), item.getGoalId(),
                item.getRuleVersionId(), aggregate));
        mapper.aggregateSubjectProfiles(item.getUserId(), item.getGoalId(), item.getRuleVersionId());
        mapper.aggregateOverallProfile(item.getUserId(), item.getGoalId(), item.getRuleVersionId());
    }
}
