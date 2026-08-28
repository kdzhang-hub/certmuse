package org.dromara.certmuse.catalog.support;

import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Feature-specific adapter for the configured OSS provider.
 */
@Component
public class ImportStorage {

    public void upload(String key, Path path) {
        OssFactory.instance().upload(key, path);
    }

    public <T> T read(String key, Function<InputStream, T> reader) {
        return OssFactory.instance().download(key, (result, stream) -> reader.apply(stream));
    }

    public void delete(String key) {
        OssFactory.instance().delete(key);
    }

    public boolean deleteQuietly(String key) {
        try {
            delete(key);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
