package org.dromara.certmuse.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.dromara.certmuse.profile.domain.LearningProfileScoreRow;
import org.dromara.certmuse.profile.mapper.LearningProfileQueryMapper;
import org.dromara.certmuse.shared.web.CertMuseApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class LearningProfileQueryServiceImplTest {

    @Test
    void returnsPersistedPrecisionAndCalculationTime() {
        LearningProfileQueryMapper mapper = mock(LearningProfileQueryMapper.class);
        OffsetDateTime calculatedTime = OffsetDateTime.parse("2026-08-17T10:20:00+08:00");
        LearningProfileScoreRow row = row(new BigDecimal("72.43750000"), calculatedTime);
        when(mapper.selectCurrentOverallScore(7L)).thenReturn(row);

        var result = new LearningProfileQueryServiceImpl(mapper).overallScore(7L);

        assertThat(result.certificationName()).isEqualTo("系统架构设计师");
        assertThat(result.overallScore()).isEqualByComparingTo("72.43750000");
        assertThat(result.calculatedTime()).isEqualTo(calculatedTime);
        verify(mapper).selectCurrentOverallScore(7L);
    }

    @Test
    void returnsBothNullableProjectionFieldsTogether() {
        LearningProfileQueryMapper mapper = mock(LearningProfileQueryMapper.class);
        OffsetDateTime calculatedTime = OffsetDateTime.parse("2026-08-17T10:20:00+08:00");
        when(mapper.selectCurrentOverallScore(1L)).thenReturn(row(null, calculatedTime));
        when(mapper.selectCurrentOverallScore(2L)).thenReturn(row(new BigDecimal("80.0"), null));

        var missingScore = new LearningProfileQueryServiceImpl(mapper).overallScore(1L);
        var missingTime = new LearningProfileQueryServiceImpl(mapper).overallScore(2L);

        assertThat(missingScore.overallScore()).isNull();
        assertThat(missingScore.calculatedTime()).isNull();
        assertThat(missingTime.overallScore()).isNull();
        assertThat(missingTime.calculatedTime()).isNull();
    }

    @Test
    void rejectsMissingCurrentGoalWithStableConflict() {
        LearningProfileQueryMapper mapper = mock(LearningProfileQueryMapper.class);
        when(mapper.selectCurrentOverallScore(7L)).thenReturn(null);

        assertThatThrownBy(() -> new LearningProfileQueryServiceImpl(mapper).overallScore(7L))
            .isInstanceOfSatisfying(CertMuseApiException.class, exception -> {
                assertThat(exception.status()).isEqualTo(409);
                assertThat(exception.errorCode()).isEqualTo("LEARNING_GOAL_NOT_ACTIVE");
                assertThat(exception.retryable()).isFalse();
                assertThat(exception.apiFieldErrors()).isEmpty();
                assertThat(exception.details()).isNull();
            });
    }

    private LearningProfileScoreRow row(BigDecimal score, OffsetDateTime calculatedTime) {
        LearningProfileScoreRow row = new LearningProfileScoreRow();
        row.setCertificationName("系统架构设计师");
        row.setOverallScore(score);
        row.setCalculatedTime(calculatedTime);
        return row;
    }
}
