package org.dromara.certmuse.question.service;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class QuestionImageUrlServiceTest {

    private final QuestionImageUrlService service = new QuestionImageUrlService();

    @Test
    void preservesOriginalUrlWhenThereIsNoStoragePath() {
        assertThat(service.accessUrl("https://example.com/question.png", null))
            .isEqualTo("https://example.com/question.png");
        assertThat(service.accessUrl("https://example.com/question.png", "   "))
            .isEqualTo("https://example.com/question.png");
    }

    @Test
    void returnsFiveMinutePresignedUrlForStoredImage() {
        OssClient client = mock(OssClient.class);
        when(client.presignGetUrl("questions/q-1/image-1.png", Duration.ofMinutes(5)))
            .thenReturn("https://oss.example.com/signed");

        try (MockedStatic<OssFactory> oss = mockStatic(OssFactory.class)) {
            oss.when(OssFactory::instance).thenReturn(client);

            assertThat(service.accessUrl("source-url", "questions/q-1/image-1.png"))
                .isEqualTo("https://oss.example.com/signed");
        }

        verify(client).presignGetUrl("questions/q-1/image-1.png", Duration.ofMinutes(5));
    }
}
