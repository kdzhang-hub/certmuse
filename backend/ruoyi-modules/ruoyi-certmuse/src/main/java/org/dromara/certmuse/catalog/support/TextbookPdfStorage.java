package org.dromara.certmuse.catalog.support;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.oss.model.Options;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/** Private OSS adapter for original textbook PDFs. */
@Component
public class TextbookPdfStorage {
    public void upload(String key, InputStream input, long size) {
        OssFactory.instance().upload(key, input, size, Options.builder().setContentType("application/pdf"));
    }

    /** Streams an object or one S3 byte range through the platform OSS abstraction. */
    public void write(String key, String range, OutputStream output) {
        try (ResponseInputStream<GetObjectResponse> input = OssFactory.instance().doCustomDownload(builder -> {
            builder.bucket(OssFactory.instance().config().bucket().orElseThrow()).key(key);
            if (range != null) {
                builder.range(range);
            }
        }, AsyncResponseTransformer.toBlockingInputStream(), null)) {
            input.transferTo(output);
        } catch (IOException | RuntimeException exception) {
            throw TextbookPdfStreamException.storageUnavailable(exception);
        }
    }

    public void delete(String key) { OssFactory.instance().delete(key); }
}
