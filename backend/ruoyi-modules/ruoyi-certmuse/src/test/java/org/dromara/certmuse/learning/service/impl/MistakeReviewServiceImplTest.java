package org.dromara.certmuse.learning.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import org.dromara.certmuse.learning.domain.MistakeIdempotencyRow;
import org.dromara.certmuse.learning.domain.MistakeSessionRow;
import org.dromara.certmuse.learning.mapper.MistakeReviewMapper;
import org.dromara.certmuse.question.service.QuestionImageUrlService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;

/** Guards the correction-session status transitions enforced by PostgreSQL. */
@Tag("dev")
class MistakeReviewServiceImplTest {
    @Test
    void productionConstructorIsExplicitlySelectedForSpringInjection() {
        assertThat(Arrays.stream(MistakeReviewServiceImpl.class.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Autowired.class)))
            .singleElement()
            .satisfies(constructor -> assertThat(constructor.getParameterCount()).isEqualTo(4));
    }

    @Test
    void completingCreatedSessionStartsItBeforeSubmitting() {
        MistakeReviewMapper mapper = Mockito.mock(MistakeReviewMapper.class);
        MistakeSessionRow created = new MistakeSessionRow();
        created.setId(20L); created.setStatus("created");
        MistakeIdempotencyRow idempotency = new MistakeIdempotencyRow();
        idempotency.setId(30L);
        when(mapper.insertIdempotency(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
        when(mapper.lockOwnedSession(20L, 10L)).thenReturn(created);
        when(mapper.countSubmitted(20L)).thenReturn(0);
        when(mapper.selectIdempotency(anyString(), anyString())).thenReturn(null, idempotency);
        MistakeReviewServiceImpl service = new MistakeReviewServiceImpl(mapper, JsonMapper.builder().build(),
            Mockito.mock(QuestionImageUrlService.class));

        var result = service.complete(10L, 20L, "3e52599b-1dde-4f56-a1b7-69987a83f934");

        assertThat(result.sessionStatus()).isEqualTo("COMPLETED");
        InOrder order = inOrder(mapper);
        order.verify(mapper).startSession(20L, 10L);
        order.verify(mapper).submitSession(20L, 10L);
        order.verify(mapper).settleSession(20L, 10L);
        order.verify(mapper).completeSession(20L, 10L);
    }
}
