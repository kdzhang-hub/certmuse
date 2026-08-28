package org.dromara.certmuse.profile.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.certmuse.profile.domain.LearningProfileScoreRow;
import org.dromara.certmuse.profile.domain.vo.OverallScoreVo;
import org.dromara.certmuse.profile.mapper.LearningProfileQueryMapper;
import org.dromara.certmuse.profile.service.LearningProfileQueryService;
import org.dromara.certmuse.shared.web.CertMuseApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only implementation backed by persisted learning-profile projections. */
@Service
@RequiredArgsConstructor
public class LearningProfileQueryServiceImpl implements LearningProfileQueryService {
    private final LearningProfileQueryMapper learningProfileQueryMapper;

    @Override
    @Transactional(readOnly = true)
    public OverallScoreVo overallScore(long userId) {
        LearningProfileScoreRow row = learningProfileQueryMapper.selectCurrentOverallScore(userId);
        if (row == null) {
            throw new CertMuseApiException(
                409, "LEARNING_GOAL_NOT_ACTIVE", "当前没有可查看的学习目标");
        }
        if (row.getOverallScore() == null || row.getCalculatedTime() == null) {
            return new OverallScoreVo(row.getCertificationName(), null, null);
        }
        return new OverallScoreVo(
            row.getCertificationName(), row.getOverallScore(), row.getCalculatedTime());
    }
}
