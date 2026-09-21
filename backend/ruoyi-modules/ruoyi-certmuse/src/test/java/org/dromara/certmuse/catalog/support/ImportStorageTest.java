package org.dromara.certmuse.catalog.support;

import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.GetObjectResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ImportStorageTest {

    @Test
    void delegatesUploadReadAndDeleteToTheConfiguredOssClient() throws Exception {
        OssClient client = mock(OssClient.class);
        ImportStorage storage = new ImportStorage();
        Path source = Path.of("source.jsonl");
        when(client.download(eq("imports/source.jsonl"),
            org.mockito.ArgumentMatchers.<BiFunction<GetObjectResult, InputStream, String>>any()))
            .thenAnswer(invocation -> {
                BiFunction<GetObjectResult, InputStream, String> reader = invocation.getArgument(1);
                return reader.apply(null, new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));
            });

        try (MockedStatic<OssFactory> oss = mockStatic(OssFactory.class)) {
            oss.when(OssFactory::instance).thenReturn(client);

            storage.upload("imports/source.jsonl", source);
            String payload = storage.read("imports/source.jsonl",
                input -> {
                    try {
                        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (java.io.IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            storage.delete("imports/source.jsonl");

            assertThat(payload).isEqualTo("payload");
            assertThat(storage.deleteQuietly("imports/source.jsonl")).isTrue();
        }

        verify(client).upload("imports/source.jsonl", source);
        verify(client, times(2)).delete("imports/source.jsonl");
    }

    @Test
    void turnsOssDeleteFailuresIntoAFalseCompensationResult() {
        OssClient client = mock(OssClient.class);
        doThrow(new IllegalStateException("oss unavailable")).when(client).delete("imports/missing.jsonl");

        try (MockedStatic<OssFactory> oss = mockStatic(OssFactory.class)) {
            oss.when(OssFactory::instance).thenReturn(client);

            assertThat(new ImportStorage().deleteQuietly("imports/missing.jsonl")).isFalse();
        }
    }
}
