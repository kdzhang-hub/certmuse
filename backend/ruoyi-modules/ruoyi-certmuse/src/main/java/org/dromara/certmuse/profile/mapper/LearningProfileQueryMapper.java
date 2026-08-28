package org.dromara.certmuse.profile.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.profile.domain.LearningProfileScoreRow;

/** Read-only persistence operations for learner-profile queries. */
@Mapper
public interface LearningProfileQueryMapper {
    LearningProfileScoreRow selectCurrentOverallScore(@Param("userId") long userId);
}
