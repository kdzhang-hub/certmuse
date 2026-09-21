package org.dromara.certmuse.catalog.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("dev")
class TextbookPdfByteRangeTest {

    @Test
    void parsesClosedOpenAndSuffixSingleRanges() {
        assertThat(TextbookPdfByteRange.parse("bytes=2-5", 10)).isEqualTo(new TextbookPdfByteRange(2, 5));
        assertThat(TextbookPdfByteRange.parse("bytes=8-", 10)).isEqualTo(new TextbookPdfByteRange(8, 9));
        assertThat(TextbookPdfByteRange.parse("bytes=-3", 10)).isEqualTo(new TextbookPdfByteRange(7, 9));
    }

    @Test
    void rejectsMultipleMalformedAndUnsatisfiedRanges() {
        assertThatThrownBy(() -> TextbookPdfByteRange.parse("bytes=1-2,4-5", 10))
            .isInstanceOf(TextbookPdfStreamException.class)
            .extracting(exception -> ((TextbookPdfStreamException) exception).status()).isEqualTo(416);
        assertThatThrownBy(() -> TextbookPdfByteRange.parse("bytes=10-11", 10))
            .isInstanceOf(TextbookPdfStreamException.class);
        assertThatThrownBy(() -> TextbookPdfByteRange.parse("bytes=bad", 10))
            .isInstanceOf(TextbookPdfStreamException.class);
    }
}
