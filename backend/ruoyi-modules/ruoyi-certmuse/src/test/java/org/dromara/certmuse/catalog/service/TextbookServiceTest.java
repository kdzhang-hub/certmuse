package org.dromara.certmuse.catalog.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.OffsetDateTime;
import org.dromara.certmuse.catalog.domain.bo.TextbookQueryBo;
import org.dromara.certmuse.catalog.mapper.TextbookMapper;
import org.dromara.certmuse.catalog.service.impl.TextbookServiceImpl;
import org.dromara.certmuse.catalog.support.TextbookException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@Tag("dev")
class TextbookServiceTest {
    private final TextbookService service = new TextbookServiceImpl(mock(TextbookMapper.class), JsonMapper.builder().build());

    @Test
    void rejectsUnsafeSortingAndUnpairedDateRangesBeforeQuerying() {
        TextbookQueryBo unsafeSort = new TextbookQueryBo(); unsafeSort.setOrderByColumn("title desc; drop table");
        assertThatThrownBy(() -> service.list(unsafeSort)).isInstanceOf(TextbookException.class)
            .extracting("errorCode").isEqualTo("TEXTBOOK_REQUEST_INVALID");

        TextbookQueryBo unpaired = new TextbookQueryBo(); unpaired.setBeginCreateTime(OffsetDateTime.now());
        assertThatThrownBy(() -> service.list(unpaired)).isInstanceOf(TextbookException.class)
            .extracting("errorCode").isEqualTo("TEXTBOOK_REQUEST_INVALID");
    }
}
