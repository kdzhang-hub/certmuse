package org.dromara.certmuse.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.certmuse.learning.domain.OnboardingRow;

/** Queries only the current learner's onboarding state. */
@Mapper
public interface OnboardingMapper {
    OnboardingRow selectStatus(@Param("userId") Long userId);
}
